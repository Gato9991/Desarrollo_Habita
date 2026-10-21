"""Pruebas matemáticas, de lectura y de integración con las fuentes del repositorio."""
import csv
import hashlib
import json
import tempfile
import unittest
from datetime import date, datetime, timedelta
from math import pi
from pathlib import Path

from modelo import (combinar, global_habita, puntuar, distancia, elegir_estacion,
                    resumir_serie, accesibilidad, numero)
from fuentes import Fuentes, leer_csv_aire
from probar_habita import UBICACIONES, calcular, guardar

RAIZ = Path(__file__).resolve().parents[1]


class Formulas(unittest.TestCase):
    def test_ejemplo_word_completo(self):
        valores = {
            "aire": combinar("aire", {"mp25": puntuar("mp25", 35), "o3": puntuar("o3", 60)}),
            "paraderos": combinar("paraderos", {"distancia": puntuar("distancia_paradero", 150),
                                               "cantidad": puntuar("cantidad_paraderos", 8)}),
            "salud": combinar("salud", {"distancia": puntuar("distancia_salud", 800),
                                       "cantidad": puntuar("cantidad_salud", 3)}),
            "bienestar": combinar("bienestar", {"temperatura": puntuar("temperatura", 25),
                                               "humedad": puntuar("humedad", 45)}),
        }
        self.assertAlmostEqual(global_habita(valores)["puntaje_100"], 78.075)
        valores["bienestar"] = combinar("bienestar", {"temperatura": puntuar("temperatura", 25),
                                                     "humedad": None})
        r = global_habita(valores)
        self.assertAlmostEqual(r["puntaje_100"], 76.75)
        self.assertAlmostEqual(r["cobertura"], .9)
        self.assertAlmostEqual(r["participacion_final"]["bienestar"], 1/6)

    def test_fronteras_y_saturacion(self):
        for c, pares in {
            "mp25": [(0,100),(100,0),(200,0)], "o3": [(0,100),(100,50),(200,0)],
            "temperatura": [(4,0),(22,100),(40,0)], "humedad": [(0,0),(50,100),(100,0)],
            "distancia_paradero": [(0,100),(500,50),(1000,0),(2000,0)],
            "distancia_salud": [(0,100),(2500,50),(5000,0)],
            "cantidad_paraderos": [(0,0),(5,50),(10,100),(30,100)],
            "cantidad_salud": [(0,0),(3,60),(5,100)],
        }.items():
            for x, esperado in pares:
                with self.subTest(c=c, x=x):
                    self.assertAlmostEqual(puntuar(c,x), esperado)

    def test_invalidos_y_ceros(self):
        for v in (None, "", "NaN", float("inf"), True):
            self.assertIsNone(numero(v))
        for c, x in (("mp25",-1),("humedad",101),("cantidad_salud",1.5)):
            self.assertIsNone(puntuar(c,x))
        self.assertEqual(puntuar("humedad",0),0)
        self.assertEqual(combinar("aire",{"mp25":0,"o3":None})["cobertura"], .7)

    def test_piso_cobertura(self):
        p = {k: combinar(k,{c:100 for c in cs}) for k,cs in
             {"aire":["mp25","o3"],"paraderos":["distancia","cantidad"],
              "salud":["distancia","cantidad"],"bienestar":["temperatura","humedad"]}.items()}
        p["aire"] = combinar("aire", {"mp25":None,"o3":None})
        self.assertEqual(global_habita(p)["puntaje_100"],100)
        self.assertEqual(global_habita(p)["cobertura"],.75)
        p["bienestar"] = combinar("bienestar", {"temperatura":None,"humedad":None})
        self.assertIsNone(global_habita(p)["puntaje_100"])
        self.assertEqual(global_habita(p)["estado"],"Cobertura insuficiente")
        self.assertIsNone(global_habita({})["puntaje_100"])

    def test_distancia_y_monotonicidad(self):
        self.assertEqual(distancia(-33,-70,-33,-70),0)
        self.assertAlmostEqual(distancia(0,0,0,1),6_371_000*pi/180)
        self.assertGreater(puntuar("distancia_paradero",100),puntuar("distancia_paradero",200))
        self.assertLess(puntuar("cantidad_salud",1),puntuar("cantidad_salud",2))

    def test_estacion_elegible_empate_y_maximo(self):
        def est(code,lon,score):
            return {"codigo":code,"latitud":0,"longitud":lon,
                    "resumenes":{"temperatura":{"puntaje_100":score}}}
        estaciones = [est("3",0,None),est("2",.1,50),est("1",.1,70)]
        self.assertEqual(elegir_estacion(0,0,estaciones)["codigo"],"1")
        self.assertIsNone(elegir_estacion(0,0,[est("9",1,80)]))

    def test_vecino_fuera_del_radio_de_conteo(self):
        sitios = [{"codigo":"X","nombre":"X","latitud":0,"longitud":.01}]
        r = accesibilidad("paraderos",0,0,sitios,500)
        self.assertEqual(r["cantidad"],0)
        self.assertGreater(r["distancia_m"],500)
        self.assertIsNotNone(r["puntaje_100"])
        self.assertIsNone(accesibilidad("salud",0,0,[],2000)["puntaje_100"])


class Series(unittest.TestCase):
    def test_puntuar_antes_de_promediar(self):
        inicio,fin=date(2026,7,1),date(2026,8,1)
        obs=[(datetime(2026,m,1),v) for m,v in [(7,4),(8,40)]]
        # Ventana continua: julio no alcanza 70 %, agosto sí, por tanto no llega a dos meses.
        r=resumir_serie(obs,"temperatura","diaria",inicio,fin)
        self.assertIsNone(r["puntaje_100"])
        self.assertEqual(r["meses"][0]["puntaje_100"],0)
        self.assertEqual(r["meses"][1]["puntaje_100"],0)

    def test_umbral_horario_conflictos_y_octubre(self):
        obs=[]
        for mes in (7,8,9):
            for dia in range(1,32):
                try:d=datetime(2026,mes,dia)
                except ValueError:continue
                obs.extend((d+timedelta(hours=h),22) for h in range(18))
        # Nueve días de octubre son insuficientes: se exigen >= 70 % de 13, es decir 10.
        for dia in range(1,10):
            obs.extend((datetime(2026,10,dia,h),22) for h in range(18))
        r=resumir_serie(obs,"temperatura")
        self.assertEqual(r["meses"][-1]["dias_esperados"],13)
        self.assertFalse(r["meses"][-1]["admisible"])
        self.assertEqual(r["puntaje_100"],100)
        # Una marca duplicada idéntica no suma horas. Una incompatible excluye esa hora.
        obs += [(datetime(2026,7,1),22),(datetime(2026,7,1),23)]
        r=resumir_serie(obs,"temperatura")
        self.assertEqual(r["marcas_en_conflicto"],1)
        self.assertEqual(r["meses"][0]["dias_admisibles"],30)

    def test_cero_sinca_prioridad_y_no_validados(self):
        with tempfile.TemporaryDirectory() as tmp:
            p=Path(tmp)/"aire.csv"
            p.write_text("FECHA (YYMMDD);HORA (HHMM);Registros validados;Registros preliminares;Registros no validados;\n"
                         "260701;0000;0;99;100;\n260702;0000;;10;20;\n260703;0000;;;55;\n",
                         encoding="utf-8")
            datos,conteos,errores=leer_csv_aire(p)
            self.assertEqual([v for _,v in datos],[0,10])
            self.assertEqual(conteos["no_validados"],3)
            self.assertEqual(errores,0)

    def test_excel_con_hoja_de_respaldo(self):
        import openpyxl
        with tempfile.TemporaryDirectory() as tmp:
            raiz=Path(tmp)
            carpeta=raiz/"FUENTES"/"CONFORT TERMICO"/"Ejemplo"
            carpeta.mkdir(parents=True)
            for mes,ndias in ((7,31),(8,31)):
                w=openpyxl.Workbook()
                s=w.active;s.title="Datos";s.append(["Fecha","Hora (UTC)","Ts (°C)"])
                for dia in range(1,ndias+1):
                    for h in range(24):
                        s.append([f"{dia:02d}-{mes:02d}-2026",f"{h:02d}:00",22])
                w.create_sheet("Texto original").append(["Respaldo original"])
                w.save(carpeta/f"{mes}.xlsx")
            f=Fuentes(raiz)
            cfg={"estaciones":[{"codigo":"1","nombre":"Ejemplo","carpeta":"Ejemplo",
                                "latitud":0,"longitud":0}]}
            r=f.clima(cfg)[0]["resumenes"]["temperatura"]
            self.assertEqual(r["puntaje_100"],100)
            self.assertEqual(r["meses_usados"],["2026-07","2026-08"])
            self.assertEqual(f.avisos,[])


class Integracion(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.datos=calcular(RAIZ)

    def test_fuentes_completas_y_no_inventadas(self):
        d=self.datos
        self.assertEqual(d["catalogos"]["dmc"]["archivos_excel"],127)
        self.assertEqual(d["catalogos"]["salud"]["registros"],5295)
        self.assertEqual(d["catalogos"]["salud"]["establecimientos_admisibles"],3690)
        self.assertGreater(d["catalogos"]["paraderos"]["paraderos_admisibles"],12000)
        self.assertEqual(d["avisos"],[])
        self.assertEqual(d["catalogos"]["aire"]["csv"],19)
        self.assertEqual(len(d["resultados"]),8)
        for r in d["resultados"]:
            self.assertIsNotNone(r["parametros"]["aire"]["puntaje_100"])
            esperado=sum(.25*p["cobertura"] for p in r["parametros"].values())
            self.assertAlmostEqual(r["indice"]["cobertura"],esperado)
            self.assertGreaterEqual(r["indice"]["cobertura"],.925)

    def test_vecinos_y_algebra_independiente(self):
        d=self.datos
        self.assertEqual([r["ubicacion"] for r in d["resultados"]],list(UBICACIONES))
        for r in d["resultados"]:
            p=r["parametros"]
            self.assertIn(p["bienestar"]["estacion"]["codigo"],
                          ["330019","330020","330021","330026"])
            for nombre,radio in (("paraderos",500),("salud",2000)):
                a=p[nombre]
                self.assertEqual(a["cantidad"]>0,a["distancia_m"]<=radio)
            c=sum(.25*x["cobertura"] for x in p.values())
            esperado=sum(.25*x["cobertura"]*x["puntaje_100"] for x in p.values() if x["puntaje_100"] is not None)/c
            self.assertAlmostEqual(r["indice"]["puntaje_100"],esperado)
            self.assertEqual(p["bienestar"]["cobertura"],1)
            self.assertEqual(p["aire"]["puntaje_100"],p["aire"]["estacion"]["indice"]["puntaje_100"])
        # Estos puntos tienen estaciones diferentes: no heredan un único promedio de RM.
        estaciones={r["parametros"]["bienestar"]["estacion"]["codigo"] for r in d["resultados"]}
        self.assertGreaterEqual(len(estaciones),3)

    def test_referencia_gtfs_existente(self):
        carpeta=RAIZ/"HABITA_GTFS_Movilidad_v1.2.1_GUI"/"resultados"
        archivo=next(carpeta.glob("analysis_Estadio*.json"))
        referencia=json.loads(archivo.read_text("utf-8"))
        estadio=self.datos["resultados"][2]["parametros"]["paraderos"]
        self.assertEqual(estadio["cercano"]["codigo"],referencia["nearest_physical_stop"]["stop_id"])
        self.assertAlmostEqual(estadio["distancia_m"],referencia["nearest_physical_stop"]["geodesic_distance_m"],delta=.5)
        # El original incluye Metro; esta prueba restringe a paraderos de bus.
        self.assertLessEqual(estadio["cantidad"],referencia["area_analysis"]["radius_500m"]["stop_points"])

    def test_exportacion_completa_y_fuentes_intactas(self):
        with tempfile.TemporaryDirectory() as tmp:
            guardar(self.datos,Path(tmp))
            html=(Path(tmp)/"resultados_habita.html").read_text("utf-8")
            self.assertNotIn("{{",html)
            self.assertEqual(html.count("<details>"),8)
            json.loads((Path(tmp)/"detalle_habita.json").read_text("utf-8"))
            with (Path(tmp)/"resultados_habita.csv").open(encoding="utf-8-sig") as f:
                self.assertEqual(len(list(csv.DictReader(f,delimiter=";"))),8)
        for fuente in self.datos["archivos_leidos"]:
            if fuente["ruta"].startswith(("FUENTES/","HABITA_GTFS_")):
                b=(RAIZ/fuente["ruta"].split("#")[0]).read_bytes()
                self.assertEqual(hashlib.sha256(b).hexdigest(),fuente["sha256"])


if __name__=="__main__":
    unittest.main(verbosity=2)
