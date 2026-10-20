# Estado técnico Quick Trip — punto de acceso

El antiguo reporte estático fue reemplazado el 20/10/2026 para evitar estados obsoletos y commits de documentación automáticos.

- **Código fuente:** rama `main` de este repositorio.
- **Ejecuciones y versiones compiladas:** pestaña [GitHub Actions](https://github.com/marcelofgx-ctrl/traslados-android/actions).
- **Registro automático de estado:** Supabase proyecto `Traslados`, esquema privado `quicktrip_control`; tablas `modules`, `github_runs`, `incidents`, `change_batches`.
- **Actualización automática:** tarea Supabase Cron `quicktrip-public-github-control` cada 30 minutos, leyendo solamente estados finales públicos de GitHub.
- **Resumen descargable:** artefacto `quicktrip-estado-tecnico` del workflow «Actualizar estado técnico Traslados (sin compilar)». **No genera commits**.
- **Producto:** Quick Trip Web/PWA y Mapa Trayectos Android con Conductor integrado; no APK Conductor independiente.

El resultado **SUCCESS** de un workflow nunca equivale a una instalación ni una prueba física satisfactoria. En `modules.qa_status` se mantiene `physical_pending` hasta confirmación en el teléfono. La última publicación web se identifica solo con `pages build and deployment` concluido correctamente.

## Consulta rápida (con acceso administrativo a Supabase)

```sql
select module_key, qa_status, last_workflow, last_result,
       last_deployed_sha, last_deployed_at
from quicktrip_control.modules
order by module_key;

select module_key,title,status
from quicktrip_control.incidents
where status <> 'resolved' order by updated_at desc;
```

El esquema no se expone a `anon` ni `authenticated`; ninguna modificación afecta las tablas operativas de reservas, GPS ni clientes. La función de sincronización trabaja sin credenciales GitHub porque consulta solamente información pública del repositorio.

**Reanudar:** «Retomar Quick Trip desde GitHub y Supabase; revisar `quicktrip_control`, `main` y el último despliegue real».
