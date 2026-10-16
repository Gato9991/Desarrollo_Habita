import unittest
from formula_indice_habita import (
    calcular_indice_habita, calcular_calidad_aire, calcular_confort_termico,
    puntaje_mp25, puntaje_ozono, puntaje_temperatura, puntaje_humedad,
    puntaje_distancia_paradero, puntaje_distancia_salud,
    puntaje_cantidad_paraderos, puntaje_cantidad_salud, puntaje_amenaza,
)

BASE = dict(mp25=35, ozono=60, temperatura=25, humedad=45,
            distancia_paradero_m=150, paraderos_500m=8,
            distancia_salud_m=800, establecimientos_2km=3,
            remociones=20, fallas=30, volcanicas=0, inundaciones=40)

class FormulaTests(unittest.TestCase):
    def test_seis_puntos_y_coberturas(self):
        casos = [
            ({}, 78.0, 100),
            (dict(distancia_paradero_m=700, paraderos_500m=2,
                  distancia_salud_m=3500, establecimientos_2km=1,
                  remociones=60, inundaciones=70), 53.1, 100),
            (dict(mp25=28, ozono=80, temperatura=26, humedad=None,
                  distancia_paradero_m=250, paraderos_500m=6,
                  distancia_salud_m=1200, establecimientos_2km=2,
                  remociones=40, fallas=45, volcanicas=5, inundaciones=25), 69.7, 92),
            (dict(mp25=28, ozono=80, temperatura=26, humedad=None,
                  distancia_paradero_m=900, paraderos_500m=None,
                  distancia_salud_m=4000, establecimientos_2km=1,
                  remociones=70, fallas=45, volcanicas=5, inundaciones=None), 47.1, 79),
            (dict(mp25=22, ozono=None, temperatura=24, humedad=55,
                  distancia_paradero_m=100, paraderos_500m=9,
                  distancia_salud_m=600, establecimientos_2km=4,
                  remociones=55, fallas=40, volcanicas=0, inundaciones=20), 83.1, 94),
            (dict(mp25=22, ozono=None, temperatura=24, humedad=55,
                  distancia_paradero_m=500, paraderos_500m=4,
                  distancia_salud_m=2000, establecimientos_2km=None,
                  remociones=None, fallas=None, volcanicas=None, inundaciones=None), 68.2, 68),
        ]
        for cambios, indice, cobertura in casos:
            with self.subTest(indice=indice):
                r = calcular_indice_habita(**(BASE | cambios))
                self.assertEqual(round(r["indice_habita"], 1), indice)
                self.assertAlmostEqual(r["cobertura_porcentaje"], cobertura)
                self.assertAlmostEqual(sum(v["participacion_final"] for v in r["variables"].values()), 1)
                self.assertAlmostEqual(sum(v["aporte_al_indice"] for v in r["variables"].values()), r["indice_habita"])

    def test_sin_datos(self):
        r = calcular_indice_habita()
        self.assertIsNone(r["indice_habita"])
        self.assertEqual(r["cobertura"], 0)
        self.assertEqual(r["estado"], "sin_datos")

    def test_cero_no_es_ausencia(self):
        r = calcular_indice_habita(paraderos_500m=0)
        self.assertEqual(r["indice_habita"], 0)
        self.assertAlmostEqual(r["cobertura"], .08)
        self.assertEqual(r["estado"], "provisional")

    def test_reajuste_con_humedad_ausente(self):
        r = calcular_indice_habita(**(BASE | {"humedad": None}))
        c = r["variables"]["confort_termico"]
        self.assertAlmostEqual(r["cobertura"], .92)
        self.assertAlmostEqual(c["peso_efectivo"], .12)
        self.assertAlmostEqual(c["participacion_final"], .12 / .92)
        self.assertAlmostEqual(calcular_confort_termico(22)["puntaje"], 100)

    def test_un_solo_componente(self):
        r = calcular_calidad_aire(mp25=35)
        self.assertAlmostEqual(r["puntaje"], 65)
        self.assertAlmostEqual(r["cobertura"], .7)
        self.assertIsNone(calcular_calidad_aire()["puntaje"])

    def test_limites(self):
        for fn, valor in [(puntaje_mp25, 1000), (puntaje_ozono, 1000),
                          (puntaje_temperatura, 50), (puntaje_humedad, 0),
                          (puntaje_distancia_paradero, 2000),
                          (puntaje_distancia_salud, 10000), (puntaje_amenaza, 100)]:
            self.assertEqual(fn(valor), 0)
        self.assertEqual(puntaje_cantidad_paraderos(100), 100)
        self.assertEqual(puntaje_cantidad_salud(100), 100)
        self.assertEqual(puntaje_amenaza(0), 100)

    def test_entradas_invalidas(self):
        for dato in [dict(mp25=-1), dict(ozono="60"), dict(temperatura=float("nan")),
                     dict(temperatura=float("inf")), dict(humedad=101),
                     dict(distancia_paradero_m=-1), dict(paraderos_500m=1.5),
                     dict(establecimientos_2km=True), dict(fallas=101),
                     dict(inundaciones=-1)]:
            with self.subTest(dato=dato), self.assertRaises(ValueError):
                calcular_indice_habita(**dato)

if __name__ == "__main__":
    unittest.main()
