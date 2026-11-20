# Fundamentos: suposiciones y decisiones de diseño

Este documento responde al requisito del challenge *"Documentar las suposiciones y decisiones de diseño
relevantes"* y a la cláusula de la sección de base de datos que exige justificar cualquier cambio sobre el
modelo provisto. Cada decisión se presenta con su **contexto**, la **decisión** tomada, los
**fundamentos** que la sostienen y sus **consecuencias** (incluidos los trade-offs).

## Índice

1. [Alcance y supuestos funcionales](#1-alcance-y-supuestos-funcionales)
2. [Stack tecnológico](#2-stack-tecnológico)
3. [Arquitectura](#3-arquitectura)
4. [Patrón Strategy](#4-patrón-strategy)
5. [Transacciones y concurrencia](#5-transacciones-y-concurrencia)
6. [Base de datos y migraciones](#6-base-de-datos-y-migraciones)
7. [Estrategia de testing](#7-estrategia-de-testing)

---

## 1. Alcance y supuestos funcionales

El enunciado deja varios puntos abiertos. Estos son los supuestos adoptados:

| # | Tema | Supuesto | Fundamento |
|---|------|----------|------------|
| 1 | Fuente de verdad | La tabla `orders` es un **libro de movimientos**; posiciones y cash se **derivan** de ella. No existen tablas de saldos. | El enunciado indica calcular tenencia y pesos con "todos los movimientos pertinentes de `orders`, usando `size`". Evita inconsistencias entre un saldo almacenado y su historial. |
| 2 | Estado `NEW` | Una orden `LIMIT` en `NEW` **reserva** recursos (cash en compras, acciones en ventas), pero no los consume. | Evita sobrecomprometer recursos con varias órdenes abiertas sin simular un motor de ejecución (el enunciado aclara que *no hace falta simular el mercado*). |
| 3 | Precio `MARKET` | Se ejecuta de inmediato al último `close` y queda `FILLED`. | Indicado por el enunciado. |
| 4 | Orden por monto | Cantidad = `floor(monto / precio)`; el remanente se descarta. Si el resultado es 0 se responde error de orden inválida. | El enunciado exige la "cantidad máxima sin fracciones". |
| 5 | Rechazos | Un rechazo por falta de fondos o acciones es una **decisión de negocio persistida** (`REJECTED`, HTTP 201), no un error de la API. | El enunciado pide guardar la orden como `REJECTED`. El motivo (`rejectionReason`) se devuelve en la respuesta, pero no se persiste (no hay columna para ello). |
| 6 | Cancelación | Solo `NEW` es cancelable; el resto responde `409`. Una orden de otro usuario responde `404`, para no revelar su existencia. | Enunciado + criterio de no filtrar información entre usuarios. |
| 7 | Transferencias | `CASH_IN` / `CASH_OUT` se modelan como órdenes sobre el instrumento de tipo `MONEDA` (ARS), con `type = MARKET`. | Indicado por el enunciado; `type` pasó a ser `NOT NULL` en la V3. |
| 8 | Rendimiento total | Se calcula con **costo promedio móvil** y excluye ganancias ya realizadas. | Es el criterio habitual de posición abierta: el rendimiento refleja solo lo que el usuario sigue teniendo. |
| 9 | Valor total | `totalValue = cash liquidado + Σ valor de mercado`. Las reservas **no** lo reducen. | Una reserva es un compromiso, no una pérdida de patrimonio. |
| 10 | Instrumentos `MONEDA` | No son operables vía `placeOrder`. | El cash se mueve solo mediante transferencias, no mediante compra/venta. |
| 11 | Sin autenticación | `userId` viaja en la ruta. | El enunciado indica que no es necesaria. |

Las fórmulas completas están en el [README](../README.md#diseño-y-decisiones).

---

## 2. Stack tecnológico

| Decisión | Detalle |
|----------|---------|
| **Java 21 + Spring Boot 3** | Se eligió por el ecosistema maduro de transacciones, validación y testing que ofrece, y por la tipación estática, que aporta seguridad en un dominio con dinero. |
| **PostgreSQL + Flyway** | La base de datos es la del enunciado. Flyway versiona el esquema como código y hace reproducible el entorno (`V1`, `V2`, `V3`). Hibernate **no** modifica el esquema. |
| **Spring Data JPA + consultas nativas puntuales** | JPA cubre el CRUD; las consultas que aprovechan funciones propias de PostgreSQL (`ILIKE`, `FOR UPDATE`, última cotización) son nativas. |
| **MapStruct** | Mapeo en tiempo de compilación entre entidades, modelos y DTOs: sin reflexión y con errores detectados al compilar. |
| **`BigDecimal` en todo el cálculo** | Evita errores de punto flotante en dinero. Sin redondeos intermedios; el redondeo (`HALF_UP`) ocurre solo al serializar. |
| **Lombok (`@Builder`, `@RequiredArgsConstructor`)** | Reduce boilerplate. Los objetos con builder se construyen con `builder()`, no con `new`, por legibilidad y para evitar errores por orden de argumentos. |
| **Docker Compose + Testcontainers** | Entorno local reproducible y pruebas de integración contra PostgreSQL real (no H2), para no validar contra un motor con semántica distinta. |

---

## 3. Arquitectura

### 3.1 Hexagonal (Ports & Adapters) con principios de Clean Architecture

**Contexto.** La lógica de negocio (posiciones, rechazos, reservas) es la parte de mayor valor y riesgo del
sistema. El transporte (REST) y la persistencia (JPA/PostgreSQL) son detalles que pueden cambiar.

**Decisión.** Se adopta arquitectura hexagonal, que comparte con Clean Architecture la **regla de
dependencia**: las dependencias de código apuntan siempre hacia el dominio.

```text
infrastructure (rest, persistence, config)
        │  depende de
        ▼
application (casos de uso)
        │  depende de
        ▼
domain (modelos, ports, strategies, excepciones)   ← no depende de la infraestructura
```

| Capa | Paquete | Responsabilidad |
|------|---------|-----------------|
| Dominio | `domain` | Modelos, reglas de negocio, strategies, excepciones y **ports** (interfaces). |
| Aplicación | `application` | Casos de uso: orquestan el flujo y definen los límites transaccionales. |
| Infraestructura | `infrastructure` | **Adapters**: controladores REST, repositorios JPA, mappers y configuración. |

**Ports & Adapters.**

- **Input ports** (`domain/ports/in`): `PlaceOrderPort`, `CancelOrderPort`, `GetPortfolioPort`,
  `SearchInstrumentsPort`. Los casos de uso los implementan; los controladores dependen de la interfaz.
- **Output ports** (`domain/ports/out`): `OrderPersistencePort`, `UserPersistencePort`, etc. Los
  adapters JPA los implementan. El dominio expresa *qué* necesita, no *cómo* se obtiene.

**Decisiones asociadas.**

| Decisión | Fundamento |
|----------|------------|
| **Modelos de dominio separados de las entidades JPA y de los DTOs** (tres representaciones, conectadas por mappers). | Cada una cambia por una razón distinta (Single Responsibility): el esquema, el contrato de la API y las reglas de negocio evolucionan de forma independiente. El costo es el código de mapeo, que MapStruct reduce. |
| **Los controladores solo traducen** (DTO ↔ comando/consulta/modelo) y delegan. | Mantiene el transporte libre de reglas de negocio. |
| **Comandos y consultas como objetos de entrada** (`PlaceOrderCommand`, `SearchInstrumentsQuery`). | Dan un tipo explícito al input de cada caso de uso (separación de comandos y consultas, siguiendo la nomenclatura de CQRS). No se implementa CQRS completo: no hay modelos ni almacenes de lectura y escritura separados. |
| **Las invariantes viven en el constructor compacto del comando/consulta.** | Un objeto de entrada **nunca existe en estado inválido** (*make illegal states unrepresentable*). El caso de uso queda libre de validaciones repetidas y no depende de que el llamador haya validado antes. |
| **Excepciones propias del dominio** (`InvalidRequestException`, `InvalidOrderException`, etc.), traducidas a HTTP en un único `GlobalExceptionHandler`. | El dominio no conoce HTTP; la correspondencia excepción → código de estado se define en un solo lugar de la infraestructura. |
| **Casos de uso como fachada** que orquestan *contexts* y ports. | Cada caso de uso es una secuencia legible de pasos (bloquear usuario → decidir → calcular posiciones → rechazar o aceptar → persistir). |

**Convenciones de dependencia aplicadas.** Solo los casos de uso conocen a los *contexts* de strategy; las
strategies nunca conocen a los contexts ni a los casos de uso. Esto evita ciclos y mantiene cada pieza
testeable de forma aislada.

**Trade-off.** Hexagonal agrega interfaces y mapeos que en un CRUD simple serían innecesarios. Se justifica
aquí porque el dominio tiene reglas no triviales y porque permite testear el negocio sin framework ni base
de datos.

> **Nota de pureza.** Las strategies y contexts llevan `@Component` de Spring para el cableado por
> inyección. Es un compromiso pragmático: el dominio no depende de infraestructura (JPA, HTTP), pero sí
> de la anotación de composición. Una separación estricta movería el cableado a una clase `@Configuration`.

---

## 4. Patrón Strategy

### 4.1 Problema

Resolver `placeOrder` y el cálculo de posiciones implica ramificar según combinaciones de `side`, `type` y
`status`. Concentrar esa lógica en un solo caso de uso mediante múltiples `if/switch` viola el principio
**abierto/cerrado** (agregar una variante obliga a modificar código existente), eleva la complejidad
ciclomática y dificulta el testeo por combinación.

### 4.2 Decisión

Se aplica **Strategy (GoF)**: una familia de algoritmos intercambiables, cada uno encapsulado en su clase,
y un **Context** que selecciona el apropiado según la combinación de entrada. Cada strategy declara qué
casos soporta mediante `supports(...)`.

Hay tres familias, una por cada decisión variable del flujo de `placeOrder` y del cálculo de posiciones:

| Context | Interfaz | Variable de selección | Implementaciones |
|---------|----------|------------------------|------------------|
| `PlaceOrderContext` | `PlaceOrderStrategy` | `side` + `type` | `BuyMarket`, `SellMarket`, `BuyLimit`, `SellLimit` |
| `CheckOrderRejectionContext` | `OrderRejectionStrategy` | `side` | `BuyRejection`, `SellRejection` |
| `CalculatePositionsContext` | `CalculatePositionsStrategy` | `status` + `side` | `FilledBuy`, `FilledSell`, `FilledCashIn`, `FilledCashOut`, `NewBuy`, `NewSell`, `Rejected`, `Cancelled` |

### 4.3 Cómo funciona

- Spring inyecta en cada Context la lista de todas las strategies registradas (`List<Strategy>`).
- El Context filtra por `supports(...)` y exige **exactamente una** coincidencia. Si no hay ninguna o hay
  más de una, falla con `IllegalStateException`: es un error de **configuración**, no de datos, y se
  detecta de inmediato.
- `CalculatePositionsContext` aplica un *fold* (reducción) sobre las órdenes ordenadas por fecha: parte de
  un estado vacío y cada strategy produce el siguiente `PositionsModel`. Tras cada paso verifica la
  invariante de que el cash liquidado no sea negativo; si lo es, lanza `InconsistentDataException`.

### 4.4 Fundamentos

| Beneficio | Explicación |
|-----------|-------------|
| **Abierto/cerrado** | Una nueva variante (por ejemplo, otro tipo de orden) es una clase nueva; no se modifican las existentes. |
| **Responsabilidad única** | Cada strategy contiene una sola regla, corta y nombrada por el caso de negocio que resuelve. |
| **Testeabilidad** | Cada strategy se prueba de forma unitaria, sin Spring ni base de datos. |
| **Legibilidad del caso de uso** | `PlaceOrderUseCase` queda como una secuencia de pasos, sin ramas por combinación. |
| **Detección temprana de errores** | Si ninguna strategy soporta una combinación, o si más de una la soporta, el Context falla de forma explícita en vez de elegir una al azar o producir un resultado silenciosamente incorrecto. |

### 4.5 Trade-offs y decisiones puntuales

- Aumenta el número de clases. Se acepta a cambio de que cada una sea pequeña y trivial de razonar.
- `resolveQuantity` es un método `default` de `PlaceOrderStrategy`: es una función pura compartida por las
  cuatro strategies, y una clase base o un bean aparte añadiría una jerarquía por un solo método.
  Contrapartida: pasa a formar parte del contrato público de cada strategy.
- Las strategies de posiciones son **funciones puras** sobre un estado inmutable (`PositionsModel` con
  métodos `with*`), lo que elimina efectos laterales y simplifica sus tests.

---

## 5. Transacciones y concurrencia

**Problema.** Dos órdenes simultáneas del mismo usuario podrían leer el mismo saldo disponible y aprobarse
ambas (*lost update* / *write skew*), sobrepasando los recursos.

**Decisión.** Toda escritura (`placeOrder`, `cancel`) corre en una transacción propia
(`REQUIRES_NEW`, `READ_COMMITTED`) que comienza **bloqueando la fila del usuario** con
`SELECT ... FOR UPDATE` (bloqueo pesimista).

| Aspecto | Decisión | Fundamento |
|---------|----------|------------|
| Granularidad del lock | Por **usuario** | Las posiciones y el cash son por usuario: serializa lo necesario y permite concurrencia total entre usuarios distintos. |
| Tipo de lock | Pesimista | La contención por usuario es baja y la operación es corta; evita reintentos y mantiene el código simple frente a un enfoque optimista con reintentos. |
| Orden de operaciones | Lock → leer órdenes → decidir → insertar | El cálculo de recursos disponibles ocurre **dentro** de la sección crítica, por lo que ve todas las órdenes confirmadas. |
| Cancelación | `UPDATE ... WHERE status = 'NEW'` condicional | Es atómica: si dos cancelaciones compiten, solo una afecta una fila; la otra responde `409`. |
| Lectura de portfolio | `REPEATABLE_READ`, `readOnly` | Una lectura multi-consulta (órdenes, instrumentos, cotizaciones) ve una **foto consistente** y no toma locks de escritura. |

Esto se verifica con pruebas de concurrencia contra PostgreSQL real (ver §7).

---

## 6. Base de datos y migraciones

### 6.1 Estrategia de versionado

| Migración | Contenido | Origen |
|-----------|-----------|--------|
| `V1__schema.sql` | Esquema de referencia **idéntico** al del challenge. | Provisto |
| `V2__load_reference_data.sql` | Datos de referencia sin modificar. | Provisto |
| `V3__schema_improvements.sql` | Restricciones de integridad, ajuste de tipos e índices. | **Decisión propia** |

**Por qué una `V3` separada y no editar `V1`/`V2`.**

- **Trazabilidad.** `V1` + `V2` son la versión entregada por el challenge. Mantenerlas intactas deja
  explícito qué se recibió y qué se cambió, que es justo lo que pide el enunciado ("cualquier cambio debe
  justificarse").
- **Inmutabilidad de migraciones.** Flyway calcula un *checksum* de cada migración aplicada; editar una ya
  ejecutada rompe el entorno de quien ya la corrió. Los cambios se hacen siempre **hacia adelante**.
- **Reversibilidad de la decisión.** Se puede auditar o discutir la `V3` sin tocar el origen.

### 6.2 Integridad

| Tabla | Cambio | Fundamento |
|-------|--------|------------|
| `users` | `NOT NULL` en `email` y `accountNumber` | Un usuario sin identificador no es direccionable ni auditable. |
| `instruments` | `NOT NULL` en `ticker`, `name` y `type` | La búsqueda y el filtro de instrumentos `MONEDA` dependen de estas columnas; un nulo produciría resultados silenciosamente incompletos. |
| `orders` | `NOT NULL` en `instrumentId`, `userId`, `size`, `type`, `side`, `status` y `datetime` | La referencia permitía nulos en todo. Una orden sin `userId` o sin `status` carece de sentido y haría fallar el cálculo de posiciones. Es mejor rechazarlo en la base que defender cada consulta. |
| `marketdata` | `NOT NULL` en `instrumentId`, `date`, `close` y `previousClose` | Son los datos mínimos para valuar una posición y calcular el retorno diario. |
| `orders` | `CHECK` en `side`, `status` y `type` | Restringe el dominio de valores a los del enunciado. Con `VARCHAR` libre, un typo (`"fiiled"`) corrompe silenciosamente el cálculo. Se eligió `CHECK` sobre `ENUM` nativo porque agregar un valor a un `CHECK` es un `ALTER` simple, mientras que los `ENUM` de PostgreSQL son más rígidos de evolucionar. |
| `orders` | `CHECK (size > 0)` | Una orden de cantidad cero o negativa no tiene significado y alteraría los saldos. |
| `orders` | `CHECK (price >= 0)` y `price` obligatorio solo en órdenes `LIMIT` | Una orden `LIMIT` sin precio es ilegible. **No** se exige `price IS NULL` en `MARKET` porque los datos de `V2` (`CASH_IN` y `MARKET`) traen precio; endurecerlo rompería la carga de referencia. |
| `users` | `UNIQUE` en `email` y `accountNumber` | Son identificadores naturales; evita duplicados que producirían búsquedas ambiguas. |
| `instruments` | `UNIQUE` en `ticker` | Es el identificador de mercado; un ticker repetido haría ambigua la búsqueda y la selección del instrumento. |
| `marketdata` | `UNIQUE (instrumentId, date)` | Una sola cotización por instrumento y día; hace determinista la elección de "última cotización". |

### 6.3 Tipos de datos

| Tabla | Cambio | Fundamento |
|-------|--------|------------|
| `orders` (`price`) y `marketdata` (`high`, `low`, `open`, `close`, `previousClose`) | `NUMERIC(10,2)` → `NUMERIC(18,2)` | `NUMERIC(10,2)` admite hasta 99.999.999,99; con montos altos en ARS puede desbordar. `NUMERIC(18,2)` da margen amplio sin pérdida de precisión. Se usa `NUMERIC` y no `FLOAT` por exactitud decimal. |
| `orders` (`datetime`) | Se mantiene `TIMESTAMP` sin zona | La aplicación lo interpreta **explícitamente como UTC**. Cambiarlo obligaría a modificar entidades y datos de referencia, con riesgo alto y poco beneficio. |
| Todas | Se mantienen `SERIAL` y los nombres de columna | Renombrarlos obligaría a modificar entidades JPA y consultas, con riesgo alto y poco beneficio. |

### 6.4 Índices

Los índices se diseñaron a partir de las **consultas reales** del sistema, no de forma genérica.

| Tabla | Índice | Consulta que sirve | Fundamento |
|-------|--------|--------------------|------------|
| `orders` | `(userId, instrumentId) INCLUDE (side, size, price) WHERE status = 'FILLED'` | Cálculo de portfolio y cash. | **Parcial**: indexa solo las filas `FILLED`, más pequeño y con menos costo de escritura. **Covering** (`INCLUDE`): habilita *index-only scans* sin ir al heap. |
| `orders` | `(userId, status)` | Listar órdenes por usuario/estado (reservas `NEW`, cancelación). | Acelera la lectura de reservas, que se evalúa en cada nueva orden. |
| `orders` | `(instrumentId)` | Soporte de la FK `orders → instruments`. | PostgreSQL **no** indexa automáticamente las claves foráneas; sin índice, un borrado o *join* por instrumento escanea toda la tabla. |
| `marketdata` | `UNIQUE (instrumentId, date DESC)` | Última cotización por instrumento. | Cumple dos funciones: restricción de unicidad e índice ordenado para obtener el último registro sin ordenar. |
| `instruments` | GIN `pg_trgm` sobre `ticker` y `name` | Búsqueda `ILIKE '%texto%'`. | Un índice B-Tree no sirve para comodines al inicio; los trigramas sí. Trade-off aceptado: **requiere permiso `CREATE EXTENSION`** y encarece levemente las escrituras en una tabla que casi no se escribe. |

**Cuestiones operativas.**

- La `V3` puede fallar en una base con datos propios si hay duplicados o nulos previos; el encabezado de la
  migración lo advierte.
- No se agregaron índices especulativos: cada uno responde a una consulta concreta, para no pagar costo de
  escritura sin beneficio medible.
- La búsqueda trata `%`, `_` y `\` como texto literal (se escapan) y ordena de forma estable por
  `(ticker, id)`, de modo que la paginación sea determinista.

---

## 7. Estrategia de testing

La estrategia sigue la **pirámide de tests**: una base amplia de tests unitarios rápidos y
determinísticos, y una cúspide acotada de tests de punta a punta que validan que las piezas funcionan
integradas contra la infraestructura real.

### 7.1 Test funcional de `placeOrder` (requisito del challenge)

El enunciado exige *"implementar un test funcional sobre la función para enviar una orden"*. Se cubre con
`OrderApiIT`, que ejercita el sistema **completo y sin simulaciones**:

```text
HTTP (MockMvc) → Controller → Caso de uso → Strategies → Adapter JPA → PostgreSQL real (Testcontainers + Flyway)
```

- Corre sobre PostgreSQL real con las migraciones `V1`, `V2` y `V3` aplicadas, por lo que valida también
  el esquema, las restricciones y los índices en uso. Se descartó H2 porque los locks, los índices
  parciales, `pg_trgm` y `ILIKE` no tienen equivalente fiel y un test verde contra otro motor daría falsa
  confianza.
- Verifica el **efecto observable** de cada orden, no su implementación: estado persistido, saldos y
  portfolio resultante.
- Cubre los escenarios que solo se pueden probar con infraestructura real:
  - **Flujo completo** de compra, cancelación, venta y rechazo, verificando que cash y tenencias se
    mantienen consistentes a lo largo de toda la secuencia.
  - **Concurrencia**: compras simultáneas que no sobregiran el cash, ventas simultáneas que no sobrevenden
    y cancelaciones simultáneas de las que solo una prospera (ver §5).
  - **Rechazos persistidos** sin alterar saldos, y errores de validación que no dejan órdenes grabadas.

### 7.2 Tests unitarios

Los tests unitarios cubren las reglas de negocio de `domain` y `application`. Se justifican por razones
técnicas concretas:

| Fundamento | Explicación |
|------------|-------------|
| **Explosión combinatoria** | El comportamiento depende de `side × type × status`, además de límites y precisión. Recorrer todas las combinaciones de punta a punta es lento y costoso; una strategy aislada las verifica en milisegundos. |
| **Localización de fallos** | Cada strategy contiene una sola regla. Si falla un test, el defecto está en esa clase y no en alguna de las capas que atraviesa un test de punta a punta. |
| **Determinismo y velocidad** | Sin Spring, sin Docker y sin base de datos: la retroalimentación es inmediata y no hay tests inestables por infraestructura. |
| **Alcance de lo inalcanzable por la API** | Datos inconsistentes, estados imposibles o errores de configuración de los contexts solo se pueden provocar construyendo el escenario de forma directa. |
| **Consecuencia del diseño** | La arquitectura hexagonal y el patrón Strategy hacen que el negocio sea **funciones puras sobre un estado inmutable**, testeables sin simular nada. Es el beneficio práctico de ese diseño. |

Complementan con un **test de contrato de API** (`@WebMvcTest`, capa web aislada) para serialización,
códigos HTTP y validaciones del borde.

### 7.3 Convenciones

- **Estilo uniforme:** `givenX_whenY_thenZ`, bloques `Given/When/Then` y mensajes `.as(...)` en cada
  aserción, para que un fallo se explique por sí solo.
- **Cobertura como herramienta de análisis, no como objetivo:** se mide con JaCoCo (`mvn verify`) sobre
  `domain` y `application`. Hay 100 % de líneas; la única rama no cubierta es inalcanzable por diseño (el
  comando ya valida `amount > 0`).
