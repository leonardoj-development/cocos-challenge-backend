# Cocos Challenge Backend

## Resumen

Desarrollar una API que permita obtener información y operar a través de los siguientes endpoints:

### Portfolio

Devolver el valor total de la cuenta de un usuario, sus pesos disponibles para operar y el listado de activos que posee. Para cada activo, incluir:

- Cantidad de acciones.
- Valor total monetario de la posición ($).
- Rendimiento total (%).

### Buscar activos

Devolver el listado de activos del mercado similares a la búsqueda realizada. La búsqueda debe soportar ticker y/o nombre.

### Enviar una orden al mercado

Permitir el envío de órdenes de compra o venta de activos, de los siguientes tipos:

| Tipo | Precio | Ejecución |
|---|---|---|
| `MARKET` | No requiere enviar un precio. | Se ejecuta con las ofertas del mercado. |
| `LIMIT` | Requiere el precio al que el usuario quiere ejecutar la orden. | La orden se envía al mercado con el precio indicado. |

La orden quedará grabada en la tabla `orders` con el estado y los valores correspondientes.

### Opcional

Proveer una colección de Postman, Insomnia o REST Client para la API, junto con ejemplos de cómo invocarla.

## Consideraciones funcionales

### Precios y mercado

- Los precios de los activos tienen que estar en pesos.
- **No hace falta simular el mercado.**
- La tabla `marketdata` contiene los precios de los últimos dos días de los instrumentos.
- `close` es el último precio de cada activo. Para calcular el retorno diario, utilizar `close` y `previousClose`.
- Para una orden `MARKET`, utilizar el último precio (`close`).

### Cantidad y tipo de operación

Al enviar una orden, el usuario debe indicar la cantidad de acciones que quiere comprar o vender. Se debe permitir que envíe:

- La cantidad exacta de acciones; o
- Un monto total de inversión en pesos. En este caso, calcular la cantidad máxima de acciones que puede enviar, sin admitir fracciones.

El atributo `side` describe si la orden es de compra (`BUY`) o venta (`SELL`).

### Estados de las órdenes

| Estado | Descripción |
|---|---|
| `NEW` | Una orden límite es enviada al mercado con este estado. |
| `FILLED` | La orden se ejecutó. Las órdenes market se ejecutan inmediatamente al ser enviadas. |
| `REJECTED` | El mercado rechazó la orden por no cumplir los requerimientos, por ejemplo, por exceder el monto disponible. |
| `CANCELLED` | El usuario canceló la orden. |

- Una orden `MARKET` se ejecuta inmediatamente y queda en estado `FILLED`.
- Una orden `LIMIT` queda en estado `NEW`.
- Solo se pueden cancelar órdenes en estado `NEW`.
- Si una orden supera los recursos disponibles, debe rechazarse y guardarse como `REJECTED`. Para una compra, validar que el usuario tenga pesos suficientes; para una venta, validar que tenga acciones suficientes.

### Transferencias, tenencia y posiciones

- Las transferencias pueden modelarse como órdenes: las entrantes tienen `side` `CASH_IN` y las salientes, `CASH_OUT`.
- Cuando se ejecuta una orden, se debe actualizar el listado de posiciones del usuario.
- Para calcular la tenencia y los pesos disponibles, utilizar todos los movimientos pertinentes de la tabla `orders`, usando la columna `size`.
- El cash (ARS) está modelado como un instrumento de tipo `MONEDA`.
- Para calcular el valor de mercado, rendimiento y cantidad de acciones de cada posición, usar las órdenes en estado `FILLED` de cada activo.

## Consideraciones técnicas

### API REST

- Desarrollar la aplicación utilizando Node.js.
- Utilizar un framework REST a elección, como Express o NestJS.
- Elegir una estrategia o librería para el acceso a datos. Es posible utilizar un ORM o ejecutar consultas directamente.
- Utilizar cualquier librería o framework que se considere conveniente.

### Calidad y documentación

- Implementar un test funcional sobre la función para enviar una orden.
- **No es necesario** implementar autenticación de usuarios.
- Documentar las suposiciones y decisiones de diseño relevantes.

## Base de datos

Como referencia, se ha creado una base de datos con algunas filas y las siguientes tablas, en el archivo `database.sql`:

| Tabla | Columnas |
|---|---|
| `users` | `id`, `email`, `accountNumber` |
| `instruments` | `id`, `ticker`, `name`, `type` |
| `orders` | `id`, `instrumentId`, `userId`, `side`, `size`, `price`, `type`, `status`, `datetime` |
| `marketdata` | `id`, `instrumentId`, `high`, `low`, `open`, `close`, `previousClose`, `datetime` |

La base de datos provista es un modelo funcional que sirve tal como está, aunque es una implementación básica. El candidato tiene la libertad de modificar, agregar o ajustar tablas para mejorar la performance, optimizar consultas o por cualquier otra razón técnica relevante. Cualquier cambio debe justificarse adecuadamente en la documentación.
