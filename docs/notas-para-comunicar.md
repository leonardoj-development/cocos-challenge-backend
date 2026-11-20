# Notas para comunicar

Hallazgos y decisiones de diseño detectados durante la revisión del código que vale la
pena comunicarle al contacto del challenge. Se van acumulando acá para pasarlos juntos
al final.

## 1. Posiciones negativas en el historial provisto

**Situación**: los datos de referencia contienen para el usuario 1 una compra de 20 acciones
de BMA y una venta posterior de 30. El saldo resultante es una posición de −10 acciones.
No se modificarán los datos provistos para eliminar esta situación.

**Alcance del enunciado**: una posición negativa puede representar una venta en descubierto,
pero los datos por sí solos no confirman que haya sido esa la operación. El challenge no
define cómo interpretar o valuar estas posiciones, ni sus garantías, costos o condiciones.
Sí exige rechazar las nuevas ventas cuando no hay acciones suficientes.

**Criterio adoptado**: aceptar el historial tal como fue entregado, mostrar la posición
negativa en el portfolio (cantidad y valor negativos) y no tratarla como un error. No se
implementa préstamo de acciones, garantías ni margen.

La regla para **nuevas órdenes** se mantiene: una venta que exceda las acciones disponibles
debe persistirse como `REJECTED`. Reconocer una posición negativa histórica no habilita
nuevas ventas en descubierto.

**Estado**: el portfolio muestra la posición negativa de BMA (cantidad y valor de mercado
negativos, descontados del valor total) y no falla. Las posiciones en cero no se listan.

**Por qué solo se omiten las posiciones en cero**: una posición cerrada no tiene exposición
(cantidad, valor y costo son 0) y su rendimiento no está definido. Una posición negativa sí
tiene exposición y afecta el valor total, por lo que se muestra.

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

## 3. Órdenes por monto: contra qué se valida el saldo

**Situación**: dos reglas del enunciado pueden dar resultados distintos y no indica cuál prevalece:

- Al enviar un monto en pesos, "calcular la cantidad de acciones máximas que puede enviar, no se admiten
  fracciones de acciones".
- "Si la orden enviada es por un monto mayor al disponible, la orden tiene que ser rechazada y guardarse
  en estado REJECTED".

Ejemplo (valores inventados solo para ilustrar): monto `$1.000`, precio `$600`, disponible `$950`.

| Criterio | Cálculo | Resultado |
|----------|---------|-----------|
| Se valida el costo real de las acciones | `floor(1000 / 600) = 1` acción, cuesta `$600` ≤ `$950` | `FILLED` |
| Se valida el monto ingresado | `$1.000` > `$950` | `REJECTED` |

**Origen de cada dato en la base de datos**:

| Concepto | Dónde está en la base | Cómo se obtiene |
|----------|----------------------|-----------------|
| Precio de la acción (MARKET) | `marketdata.close` | Fila con la `date` más reciente del instrumento (`MarketDataRepository.findLatest`). |
| Cantidad de acciones | `orders.size` (`INT`) | La base solo admite cantidades enteras, de ahí que no haya fracciones. |
| Monto ingresado | **No existe columna** | La orden persiste `size` y `price`, no el monto pedido. Solo queda el costo `size × price`. |
| Pesos disponibles | `orders` del usuario | Suma de `CASH_IN` / `CASH_OUT` (`size`, sobre el instrumento `MONEDA` ARS, `price = 1`) y de compras/ventas `FILLED` (`size × price`). |
| Pesos reservados | `orders` con `status = 'NEW'` | Compras `NEW` (`size × price`); se restan del disponible. |
| Estado de la orden | `orders.status` | `FILLED`, `NEW`, `REJECTED`, `CANCELLED`. |

**Consecuencia del esquema**: como la base no guarda el monto ingresado, lo único persistido de una orden
por monto es la cantidad calculada y el precio. Una orden `FILLED` o `REJECTED` queda registrada por su
costo real (`size × price`), no por el monto que pidió el usuario.

**Qué hace el código hoy**:

- `PlaceOrderStrategy.resolveQuantity` calcula `amount / price` (con `price` = `marketdata.close`) redondeando
  hacia abajo; si da 0, lanza `InvalidOrderException` ("amount is lower than the price of one share").
- `BuyRejectionStrategy` compara `quantity × price` contra `settledCash − reservedCash` (calculados desde
  `orders` como indica la tabla). Nunca ve el monto ingresado.

**Criterio adoptado**: se valida el **costo real** de las acciones calculadas, no el monto ingresado. Es
coherente con lo que la base puede guardar (`size` entero y `price`). Sigue siendo una interpretación
nuestra: el enunciado no define cuál regla prevalece.
