# Notas para comunicar

Hallazgos y decisiones de diseño detectados durante la revisión del código que vale la
pena comunicarle al contacto del challenge (no son bugs bloqueantes, son observaciones
de scope/criterio). Se van acumulando acá para pasarlos juntos al final.

## 1. Caso límite: venta de un instrumento sin tenencias previas

**Qué pasa**: si en el historial de un usuario aparece una venta ya efectivizada de un
instrumento del cual no tenía unidades, el sistema no sabe cómo calcular el costo de esa
posición y el pedido falla con un error.

**Por qué no es alcanzable desde la API**:
- Una orden `SELL` solo se persiste como `FILLED` si antes pasó `SellRejectionStrategy`, que exige
  `cantidad - reservadas >= size` (con `size >= 1`). Sin tenencia previa el disponible es 0 y la orden
  queda `REJECTED`.
- `PlaceOrderUseCase` toma un lock sobre el usuario, por lo que dos órdenes concurrentes no se pisan.

**Cuándo podría ocurrir**: solo con datos que no entraron por `placeOrder` (seeds, inserts manuales o
un `SELL FILLED` sin compra previa en el historial). En `FilledSellStrategy`, con `holding.quantity() == 0`,
el cálculo del costo remanente divide por cero y lanza `ArithmeticException` (500 genérico).

**Decisión**: es un problema de calidad de datos, no un escenario de negocio, por lo que no se
contempla en el código ni se cubre con tests.

**Por qué lo comunico**: es un caso que no está contemplado en el enunciado del challenge
(no se aclara qué debería pasar con datos así), así que prefiero dejarlo señalado en vez
de que parezca que no lo contemplé.

## 2. Migraciones de Flyway y base de datos del challenge

**Qué se hizo**: las migraciones de Flyway existen para levantar la base de datos de forma **local**, con su
esquema y sus datos, de manera reproducible:

| Migración | Contenido |
|-----------|-----------|
| `V1__schema.sql` | Esquema de referencia provisto por el challenge. |
| `V2__load_reference_data.sql` | Datos de referencia provistos por el challenge. |
| `V3__schema_improvements.sql` | Mejoras propuestas: integridad e índices (justificadas en [fundamentos](fundamentos.md)). |

**Configuración según el entorno**:
- `.env`: conexión a la base **local** (la que levanta Docker Compose). Flyway está habilitado y aplica
  `V1`, `V2` y `V3`.
- `.env.external-db`: conexión a la base **propuesta en el challenge**. Deshabilita Flyway
  (`SPRING_FLYWAY_ENABLED=false`) para no realizar modificaciones sobre una base que puede estar compartida
  con otros candidatos.

**Qué implica**: contra la base del challenge la aplicación funciona con el esquema original, sin las
mejoras de `V3` (restricciones e índices).

**Si se quisieran aplicar las mejoras sobre esa base**: esa base ya contiene el esquema y los datos de
`V1` y `V2`, por lo que esas migraciones **se descartan** (se eliminan del proyecto). Esto requiere
**modificar la migración de mejoras** (`V3`), que hoy está numerada y pensada como continuación de
`V1` y `V2`, para que pueda ejecutarse por sí sola sobre esa base.
