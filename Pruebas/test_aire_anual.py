"""Pruebas del resumen anual de aire y de la consulta por coordenadas."""
import csv
import json
import math
import subprocess
import sys
import tempfile
import unittest
from datetime import datetime, timedelta
from pathlib import Path

from modelo import (INICIO_AIRE, FIN_AIRE, O3_PPB_A_UG_M3,
                    concentracion_normalizada, puntuar, resumir_aire_anual)

RAIZ = Path(__file__).resolve().parents[1]
SCRIPT = Path(__file__).with_name("probar_habita.py")


def registros(n=365, valor=20, estado="validados"):
    primero = datetime.combine(INICIO_AIRE,datetime.min.time())
    return [(primero+timedelta(days=i),valor,estado) for i in range(n)]


class AireAnual(unittest.TestCase):
    def test_ventana_es_un_ano_comun(self):
        self.assertEqual((FIN_AIRE-INICIO_AIRE).days+1,365)
        self.assertEqual(str(INICIO_AIRE),"2025-10-14")
        self.assertEqual(str(FIN_AIRE),"2026-10-13")

    def test_ozono_con_conversion_de_unidad(self):
        # C = ppb * P * M / (R*T) * 1e-3, a la referencia declarada.
        esperado=101325*48/(8.31446261815324*298.15)*1e-3
        self.assertAlmostEqual(O3_PPB_A_UG_M3,esperado)
        self.assertAlmostEqual(concentracion_normalizada("o3",50,"ppb"),50*esperado)
        self.assertAlmostEqual(puntuar("o3",concentracion_normalizada("o3",50,"ppb")),
                               100*(1-50*esperado/200))
        self.assertEqual(concentracion_normalizada("mp25",20,"ug/m3"),20)
        self.assertIsNone(concentracion_normalizada("mp25",20,"ppb"))

    def test_puntaje_anual_constante(self):
        r=resumir_aire_anual(registros(valor=20),"mp25","ug/m3")
        self.assertEqual(r["puntaje_100"],80)
        self.assertEqual(r["dias_admisibles"],365)
        self.assertEqual(r["estado"],"Completo")

    def test_setenta_por_ciento_del_ano(self):
        a=resumir_aire_anual(registros(255),"mp25","ug/m3")
        b=resumir_aire_anual(registros(256),"mp25","ug/m3")
        self.assertIsNone(a["puntaje_100"])
        self.assertEqual(b["puntaje_100"],80)
        self.assertEqual(b["estado"],"Parcial temporal")

    def test_puntuar_antes_de_promediar(self):
        obs=registros()
        obs=[(d,0 if i%2==0 else 200,s) for i,(d,_,s) in enumerate(obs)]
        r=resumir_aire_anual(obs,"mp25","ug/m3")
        self.assertAlmostEqual(r["puntaje_100"],183*100/365)
        self.assertGreater(r["puntaje_100"],
                           puntuar("mp25",r["valor_medio"])+40)

    def test_no_mezcla_historia_fuera_del_ano(self):
        obs=registros()
        obs.extend([(datetime(1997,4,2),999,"validados"),
                    (datetime(2026,10,14),999,"validados")])
        r=resumir_aire_anual(obs,"mp25","ug/m3")
        self.assertEqual(r["puntaje_100"],80)
        self.assertEqual(r["fuera_periodo"],2)

    def test_validacion_duplicados_conflictos_y_ceros(self):
        obs=registros()
        primero=obs[0][0]
        # Validado manda sobre preliminar; un duplicado idéntico no suma días.
        obs.extend([(primero,20,"validados"),(primero,99,"preliminares")])
        r=resumir_aire_anual(obs,"mp25","ug/m3")
        self.assertEqual(r["dias_admisibles"],365)
        self.assertEqual(r["valor_medio"],20)
        # Dos validados incompatibles excluyen la fecha, no se promedian.
        obs.append((primero,21,"validados"))
        r=resumir_aire_anual(obs,"mp25","ug/m3")
        self.assertEqual(r["dias_admisibles"],364)
        self.assertEqual(r["dias_en_conflicto"],1)
        z=resumir_aire_anual(registros(valor=0),"o3","ppb")
        self.assertEqual(z["puntaje_100"],100)

    def test_preliminares_etiquetados_y_no_validados_excluidos(self):
        r=resumir_aire_anual(registros(300,20,"preliminares")+
                            registros(365,99,"no_validados"),"mp25","ug/m3")
        self.assertEqual(r["dias_admisibles"],300)
        self.assertEqual(r["estados_aceptados"],{"preliminares":300})
        self.assertEqual(r["no_validados_excluidos"],365)
        self.assertEqual(r["puntaje_100"],80)

    def test_estado_desconocido_e_infinito_no_se_aceptan(self):
        self.assertIsNone(resumir_aire_anual(
            registros(365,20,"otro"),"mp25","ug/m3")["puntaje_100"])
        self.assertIsNone(resumir_aire_anual(
            registros(365,float("inf")),"mp25","ug/m3")["puntaje_100"])


class CoordenadasCLI(unittest.TestCase):
    def test_falta_la_pareja_y_rechaza_nan(self):
        for args in (["--latitud","-33"],["--latitud","nan","--longitud","-70"]):
            r=subprocess.run([sys.executable,str(SCRIPT),"--no-abrir"]+args,
                             capture_output=True,text=True,encoding="utf-8")
            self.assertNotEqual(r.returncode,0)
            self.assertTrue("latitud" in r.stderr.lower() or "coordenadas" in r.stderr.lower())

    def test_una_coordenada_real_genera_un_solo_resultado(self):
        with tempfile.TemporaryDirectory() as tmp:
            r=subprocess.run([sys.executable,str(SCRIPT),"--no-abrir",
                              "--latitud","-33.4422475","--longitud","-70.653898",
                              "--nombre","La Moneda por coordenadas","--salida",tmp],
                             cwd=tmp,capture_output=True,text=True,encoding="utf-8")
            self.assertEqual(r.returncode,0,r.stderr)
            d=json.loads((Path(tmp)/"detalle_habita.json").read_text("utf-8"))
            self.assertEqual(len(d["resultados"]),1)
            p=d["resultados"][0]
            self.assertEqual(p["parametros"]["aire"]["estacion"]["codigo"],"273")
            s=p["parametros"]["aire"]["estacion"]
            self.assertEqual(p["parametros"]["aire"]["puntaje_100"],s["indice"]["puntaje_100"])
            self.assertAlmostEqual(p["indice"]["cobertura"],.925)
            page=(Path(tmp)/"resultados_habita.html").read_text("utf-8")
            self.assertIn("Una ubicación.",page)
            self.assertNotIn("{{",page)
            with (Path(tmp)/"indices_aire_estaciones.csv").open(encoding="utf-8-sig") as f:
                self.assertEqual(len(list(csv.DictReader(f,delimiter=";"))),12)


if __name__=="__main__":
    unittest.main(verbosity=2)
