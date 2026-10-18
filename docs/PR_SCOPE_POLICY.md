# G1 — control automático de alcance por PR

**Inicio:** 18/10/2026. Complementa, no sustituye, la metodología permanente G0–G6.

## Contrato

Cada PR nuevo posterior al corte metodológico del 18/10/2026 a las 11:57:04 UTC declara en su cuerpo:
- Un **propietario lógico**: ci, docs, pwa, mapa o cross-surface.
- Los **archivos exactos previstos**, sin comodines. Cada archivo adicional exige actualizar la declaración.
- Una **justificación** de al menos 20 caracteres para cambios cross-surface.

Ejemplo (incluirlo textualmente en la descripción del PR):

    <!-- traslados-scope
    owner: ci
    paths:
    - scripts/ci_pr_scope.py
    - scripts/test_ci_pr_scope.py
    - .github/workflows/pr-scope-guard.yml
    - docs/PR_SCOPE_POLICY.md
    -->

El workflow .github/workflows/pr-scope-guard.yml ejecuta pruebas Python puro y compara rutas desde merge-base hasta HEAD mediante git diff --no-renames --name-only, incluyendo los dos lados de un renombrado. Comprueba que todos los archivos reales estén declarados, que no sobren rutas en la declaración y que el propietario abarque las rutas editadas. Editar el cuerpo del PR dispara una nueva validación.

**Compatibilidad:** PR existentes antes de 11:57:04 UTC del 18/10/2026, incluidos PR apilados todavía en QA como #129, no se bloquean por carecer de declaración. Si sí tienen declaración, se valida. No modifica ramas anteriores ni estados QA.

## Límites deliberados

- No compila Android, APK ni PWA; no consulta Supabase ni toca reservas, GPS, cuentas o parámetros.
- No sustituye tests G3, CI Android G4, QA Samsung G5, check de dependencia, firma y publicación G6.
- Por sí solo no constituye protección de rama ni defensa ante cambios deliberados del workflow: para tener un bloqueo de merges real hay que comprobar y configurar reglas de protección de main en otra etapa.
- Los propietarios mapa/pwa pueden declarar rutas de tests, documentación y workflow relacionadas; una modificación de código en ambas superficies exige owner cross-surface con razón.

## Si falla

1. Revisar en GitHub Actions el archivo fuera de alcance.
2. Si el cambio fue accidental, retirarlo sin tocar otros PR.
3. Si el cambio es necesario, revisar contratos G1, justificar el impacto y ajustar el cuerpo del PR.
4. Repetir las pruebas y mantener las puertas Android/PWA correspondientes; no publicar por presión.

**Etapa posterior independiente:** medir y recortar compilaciones redundantes en validate-modular-mapa.yml con evidencia, sin eliminar la integración Android previa a cada merge que la necesite. Mantener R25.31-QA protegido hasta pruebas Samsung.
