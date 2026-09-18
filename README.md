# 🌐 VZP Request Service

**VZP Request Service** é um mini-framework HTTP para Java, construído sobre o `com.sun.net.httpserver.HttpServer` da própria JDK.

Ele existe para casos em que um framework como Spring Boot seria pesado demais: APIs internas, ferramentas administrativas, automações e serviços com baixo volume de requisições, onde inicialização rápida e baixo consumo de memória importam mais do que um ecossistema completo de recursos.

O projeto roda de **Java 8 em diante**. Ele **não implementa TLS/HTTPS** — a recomendação é colocar um proxy reverso (ex. Nginx) na frente, cuidando da terminação TLS, e deixar a aplicação Java conversar apenas em HTTP puro atrás dele.

---

## Sumário

- [Características](#características)
- [Instalação](#instalação)
- [Início rápido](#início-rápido)
- [`RequestServer`](#requestserver)
- [Controllers e rotas](#controllers-e-rotas)
- [`HttpRequest`](#httprequest)
- [`HttpResponse` e `HttpUtils`](#httpresponse-e-httputils)
- [Tratamento de erros](#tratamento-de-erros)
- [`ObjectRepository` (armazenamento em memória)](#objectrepository-armazenamento-em-memória)
- [Logging](#logging)
- [Segurança e hardening](#segurança-e-hardening)
- [Exemplo completo](#exemplo-completo)
- [Estrutura do projeto](#estrutura-do-projeto)
- [Compatibilidade](#compatibilidade)
- [Licença](#licença)

---

## Características

- Inicialização em milissegundos, baseada em `com.sun.net.httpserver.HttpServer`
- Controllers baseados em anotações, com roteamento automático (inclusive parâmetros de rota, ex. `/{id}`)
- Corpo de requisição/resposta em JSON via Gson
- Thread pool configurável, com padrão limitado (nunca thread única) e timeouts nativos de idle/leitura/escrita
- Limite configurável de tamanho de corpo de requisição (proteção contra esgotamento de memória)
- Camada de repositório em memória (`ObjectRepository`), com proteção contra *mass assignment* via `@Updatable`
- Modelo de erro único (`Errors` / `ApplicationException`) que nunca vaza stacktrace ao cliente
- Logging estruturado via `java.util.logging`, sem dependências externas
- Compatível com Java 8+

> **Sem persistência real embutida.** `ObjectRepository` guarda os objetos num `ConcurrentHashMap` em memória — é útil para protótipos, testes e serviços simples, mas os dados não sobrevivem a um restart. Se precisar de um banco de verdade, plugue sua própria camada de acesso a dados (JDBC, JPA, o que preferir) dentro do seu repositório/serviço.

---

## Instalação

O projeto é distribuído via **JitPack**.

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>
```

```xml
<dependencies>
    <dependency>
        <groupId>com.github.felippevz</groupId>
        <artifactId>vzp-request-service</artifactId>
        <version>master-SNAPSHOT</version>
    </dependency>
</dependencies>
```

---

## Início rápido

```java
public class Main {

    public static void main(String[] args) {

        RequestServer server = new RequestServer(8080);

        ProductRepository repository = new ProductRepository();
        ProductController controller = new ProductController(repository);

        server.registerController(controller);

        server.start();
    }
}
```

Isso sobe um servidor HTTP na porta `8080`, com o pool de threads e os timeouts padrão do framework já configurados (veja [`RequestServer`](#requestserver)).

---

## `RequestServer`

Classe central do framework: liga o `HttpServer` da JDK ao roteador e aos controllers registrados.

```java
RequestServer server = new RequestServer(8080);
```

### Configuração

| Método | Descrição | Padrão |
|---|---|---|
| `setBackLog(int backLog)` | Backlog de conexões TCP pendentes (`0` = valor padrão do SO) | `0` |
| `setExecutor(Executor executor)` | Substitui o thread pool padrão por qualquer `Executor` | pool próprio (ver abaixo) |
| `setMaxRequestBodyBytes(long maxBodyBytes)` | Tamanho máximo aceito para o corpo da requisição; acima disso o cliente recebe `413` | `1_048_576` (1 MB) |
| `setIdleIntervalSeconds(int seconds)` | Intervalo de verificação de conexões ociosas | `30` |
| `setMaxRequestSeconds(int seconds)` | Tempo máximo para receber uma requisição completa | `30` |
| `setMaxResponseSeconds(int seconds)` | Tempo máximo para enviar uma resposta completa | `30` |
| `registerController(Object controller)` | Registra um controller anotado com `@Controller` | — |
| `start()` | Sobe o servidor na porta configurada | — |
| `getPort()` | Retorna a porta configurada | — |

```java
RequestServer server = new RequestServer(8080);

server.setBackLog(1024);
server.setMaxRequestBodyBytes(2 * 1024 * 1024); // 2MB
server.setIdleIntervalSeconds(15);
server.setMaxRequestSeconds(15);
server.setMaxResponseSeconds(15);

// Substituindo o executor padrão por um customizado, se necessário
server.setExecutor(new ThreadPoolExecutor(
        16, 128, 60, TimeUnit.SECONDS,
        new LinkedBlockingQueue<>(1000)
));

server.registerController(new ProductController(new ProductRepository()));
server.start();
```

### Thread pool padrão

Por padrão o servidor **não** usa uma única thread. O executor padrão é um `ThreadPoolExecutor` com:

- 8 threads *core*, até 64 threads sob demanda
- fila de espera limitada a 500 requisições (evita crescimento ilimitado de memória sob carga)
- threads nomeadas (`http-worker-N`) com `UncaughtExceptionHandler` que loga qualquer falha não tratada
- política de rejeição que loga um aviso e processa a requisição na própria thread de aceitação como *fallback* (backpressure), em vez de simplesmente descartar a conexão

Se sua carga de trabalho tiver características muito diferentes (I/O-bound pesado, CPU-bound, etc.), use `setExecutor(...)` para plugar o seu próprio `Executor`.

---

## Controllers e rotas

Um controller é uma classe qualquer anotada com `@Controller("/caminho-base")`.

```java
@Controller("/products")
public class ProductController {

    private final ProductRepository repository;

    public ProductController(ProductRepository repository) {
        this.repository = repository;
    }
}
```

Os métodos do controller viram endpoints através das anotações de verbo HTTP:

| Anotação | Verbo HTTP |
|---|---|
| `@Get("/caminho")` | `GET` |
| `@Post("/caminho")` | `POST` |
| `@Put("/caminho")` | `PUT` |
| `@Patch("/caminho")` | `PATCH` |
| `@Delete("/caminho")` | `DELETE` |

O caminho é concatenado ao caminho base do `@Controller`. Segmentos entre chaves (`{id}`) viram parâmetros de rota resolvidos automaticamente — a assinatura do método recebe, na ordem, um `HttpRequest` opcional seguido de um `String` para cada parâmetro de rota:

```java
@Controller("/products")
public class ProductController {

    private final ProductRepository repository;

    public ProductController(ProductRepository repository) {
        this.repository = repository;
    }

    @Get
    public void findAll(HttpRequest request) {
        new HttpResponse()
                .addListObjects("products", new ArrayList<>(repository.findAll()))
                .send(request);
    }

    @Get("/{id}")
    public void findById(HttpRequest request, String id) {

        Product product = repository.findById(Long.parseLong(id));

        if (product == null) {
            HttpUtils.send(Errors.ENTITY_NOT_FOUND, request);
            return;
        }

        new HttpResponse().addObject("product", product).send(request);
    }

    @Post
    public void create(HttpRequest request) {
        Product product = request.getObjectBody(Product.class);
        repository.save(product);
        HttpUtils.ok(request);
    }

    @Put("/{id}")
    public void update(HttpRequest request, String id) {
        Product updated = request.getObjectBody(Product.class);
        repository.update(Long.parseLong(id), updated);
        HttpUtils.ok(request);
    }

    @Delete("/{id}")
    public void delete(HttpRequest request, String id) {
        repository.deleteById(Long.parseLong(id));
        HttpUtils.ok(request);
    }
}
```

> Não é necessário fazer `try/catch` manual em torno da lógica do controller: qualquer exceção lançada (checked ou unchecked) é capturada pelo `RequestHandler`, registrada em log e transformada numa resposta de erro apropriada para o cliente — veja [Tratamento de erros](#tratamento-de-erros).

---

## `HttpRequest`

Representa a requisição recebida. É injetado automaticamente como primeiro parâmetro de qualquer método de endpoint.

| Método | Descrição |
|---|---|
| `getMethod()` | Verbo HTTP (`"GET"`, `"POST"`, ...) |
| `getPath()` | Caminho da requisição |
| `getHeaders()` | `Map<String, String>` com os headers (primeiro valor de cada header) |
| `getBody()` | Corpo bruto da requisição, como `String` |
| `<T> getObjectBody(Class<T> objectClass)` | Desserializa o corpo JSON para o tipo informado, via Gson |
| `getExchange()` | Acesso ao `HttpExchange` original da JDK, para casos avançados |

```java
@Post
public void create(HttpRequest request) {
    Product product = request.getObjectBody(Product.class);
    String apiKey = request.getHeaders().get("X-Api-Key");
    // ...
}
```

O corpo da requisição tem um limite de tamanho aplicado antes mesmo de chegar ao seu controller — veja `setMaxRequestBodyBytes` em [`RequestServer`](#requestserver).

---

## `HttpResponse` e `HttpUtils`

`HttpResponse` monta a resposta JSON enviada ao cliente.

| Método | Descrição |
|---|---|
| `setStatus(int status)` | Define o status HTTP (padrão `200`) |
| `setHeader(String key, String value)` | Adiciona um header de resposta |
| `addFieldBody(String key, String value)` | Adiciona um campo `String` simples ao corpo |
| `addObject(String property, Object object)` | Serializa um objeto (via Gson) e adiciona ao corpo |
| `addListObjects(String property, List<Object> objects)` | Serializa uma lista e adiciona ao corpo |
| `setBody(JsonObject jsonObject)` | Substitui o corpo inteiro por um `JsonObject` já pronto |
| `clearFields()` | Limpa o corpo (mantém apenas o que for adicionado depois) |
| `send(HttpRequest request)` | Envia a resposta ao cliente. Idempotente — chamadas repetidas são ignoradas |

Toda resposta já sai com `Content-Type: application/json` e um campo `timestamp`.

```java
new HttpResponse()
        .setStatus(201)
        .addObject("product", product)
        .send(request);
```

`HttpUtils` traz atalhos para os dois casos mais comuns:

```java
HttpUtils.ok(request);                       // 200, corpo vazio
HttpUtils.send(Errors.ENTITY_NOT_FOUND, request); // status/mensagem definidos pelo Errors
```

---

## Tratamento de erros

O framework usa um modelo único de erro: o enum `Errors`, mais a exceção `ApplicationException` (que estende `GlobalException`).

```java
throw new ApplicationException(Errors.ENTITY_NOT_FOUND, causaOriginalOuNull);
```

Cada valor de `Errors` já carrega uma mensagem segura para o cliente, um código interno e o status HTTP correspondente:

| Erro | HTTP | Quando ocorre |
|---|---|---|
| `ROUTE_NOT_FOUND` | 404 | Nenhuma rota corresponde ao método/caminho |
| `ENTITY_NOT_FOUND` | 404 | `ObjectRepository.update()` chamado com um `id` inexistente |
| `PAYLOAD_TOO_LARGE` | 413 | Corpo da requisição excede o limite configurado |
| `VALUE_METHOD_CONTROLLER_ERROR` | 500 | Falha ao ler `value()` de uma anotação de rota no registro do controller |
| `METHOD_INVOKE_ERROR` | 500 | Exceção não tratada dentro de um método de endpoint |
| `ID_NOT_FOUND` | 500 | Entidade salva sem nenhum campo anotado com `@javax.persistence.Id` |
| `FIELD_COPY_ERROR` | 500 | Falha de reflection ao copiar campos em `save`/`update` |
| `RESPONSE_SEND_ERROR` | 500 | Falha de I/O ao escrever a resposta (ex.: cliente desconectou) |
| `SERVER_INIT_ERROR` | 500 | Falha ao subir o `HttpServer` |
| `INTERNAL_SERVER_ERROR` | 500 | Qualquer exceção inesperada não mapeada para um `Errors` específico |

**Você não precisa capturar exceções manualmente nos controllers.** O `RequestHandler` envolve toda a execução da requisição: qualquer `ApplicationException` lançada é convertida na resposta HTTP correspondente; qualquer outra exceção (`NullPointerException`, erro de biblioteca, etc.) é registrada em log com stacktrace completo e devolvida ao cliente como `500 Internal server error` — **sem nunca expor a mensagem ou o stacktrace original**. Isso é o que garante que uma requisição jamais "trave" ou "suma" sem deixar rastro: toda falha vira log + resposta.

---

## `ObjectRepository` (armazenamento em memória)

`ObjectRepository<T, ID>` é uma camada de armazenamento genérica e em memória (`ConcurrentHashMap`), pensada para protótipos e serviços simples.

```java
public class ProductRepository extends ObjectRepository<Product, Long> {
}
```

A entidade precisa de um campo anotado com `@javax.persistence.Id`:

```java
public class Product {

    @Id
    private Long id;

    @Updatable
    private String name;

    @Updatable
    private BigDecimal price;

    // getters/setters
}
```

| Método | Descrição |
|---|---|
| `findAll()` | Retorna todas as entidades armazenadas |
| `findById(ID id)` | Busca por id, ou `null` se não existir |
| `save(T entity)` | Persiste a entidade, usando o valor do campo `@Id` como chave. Lança `ApplicationException(ID_NOT_FOUND)` se nenhum campo estiver anotado |
| `update(ID id, T updatedEntity)` | Copia **apenas** os campos anotados com `@Updatable` de `updatedEntity` para a entidade existente. Lança `ApplicationException(ENTITY_NOT_FOUND)` se o `id` não existir |
| `deleteById(ID id)` | Remove a entidade pelo id (sem efeito se não existir) |

### Por que `@Updatable` e não copiar tudo?

Copiar todos os campos do payload recebido do cliente para a entidade persistida é um clássico problema de **mass assignment**: um payload malicioso poderia sobrescrever campos que nunca deveriam ser editáveis via API (ids, flags internas, etc.). Por isso `update()` só toca em campos explicitamente marcados com `@Updatable` — é uma allow-list, não uma blacklist.

```java
@Put("/{id}")
public void update(HttpRequest request, String id) {
    Product updated = request.getObjectBody(Product.class); // só name/price importam
    repository.update(Long.parseLong(id), updated);          // id nunca é sobrescrito
    HttpUtils.ok(request);
}
```

---

## Logging

O framework configura o próprio `java.util.logging` na primeira vez que um `RequestServer` é criado — não é necessário nenhuma dependência ou configuração extra.

```
2026-09-17 10:15:03.512 [INFO   ] dev.felippevaz.server.RequestServer - HttpServer started on port 8080
2026-09-17 10:15:07.881 [FINE   ] dev.felippevaz.handler.RequestHandler - Dispatching POST /products
2026-09-17 10:15:07.902 [SEVERE ] dev.felippevaz.handler.RequestHandler - Unhandled error handling POST /products
```

O nível de log é controlado pela propriedade de sistema `vzp.log.level` (padrão `INFO`):

```bash
java -Dvzp.log.level=FINE -jar sua-aplicacao.jar
```

Use `FINE` em desenvolvimento para acompanhar cada etapa do ciclo de vida da requisição (parsing, dispatch, invocação, envio de resposta); em produção, `INFO` (padrão) mantém o volume de log enxuto e ainda registra toda falha em `WARNING`/`SEVERE` com stacktrace completo.

---

## Segurança e hardening

- **TLS**: não implementado internamente por design — coloque a aplicação atrás de um proxy reverso (Nginx, por exemplo) responsável pela terminação TLS. A aplicação Java deve ficar acessível apenas a partir do proxy (bind em loopback/rede interna), nunca exposta diretamente à internet.
- **Cabeçalhos de proxy** (`X-Forwarded-For`, `X-Forwarded-Proto`, `Host`): `HttpRequest.getHeaders()` expõe os headers crus, sem qualquer suposição de confiança. Se sua aplicação usar esses valores (ex. para logging ou allowlist de IP), garanta que o proxy **sobrescreva** — nunca apenas acrescente — esses headers, e que não seja possível alcançar a aplicação pulando o proxy.
- **Tamanho do corpo da requisição**: limitado por padrão a 1 MB (`RequestServer.setMaxRequestBodyBytes`), aplicado tanto pelo `Content-Length` declarado quanto durante a leitura real do corpo — protege contra esgotamento de memória mesmo com `Content-Length` ausente ou forjado.
- **Timeouts**: idle/leitura/escrita configuráveis (`setIdleIntervalSeconds`, `setMaxRequestSeconds`, `setMaxResponseSeconds`), evitando que um cliente lento prenda uma thread indefinidamente.
- **Thread pool limitado**: o executor padrão nunca é uma thread única e nunca tem fila ilimitada — evita que uma única requisição lenta congele o servidor inteiro, e evita OOM por fila sem limite sob carga.
- **Mass assignment**: `ObjectRepository.update()` só atualiza campos anotados com `@Updatable` (allow-list).
- **Vazamento de stacktrace**: nenhuma exceção crua chega ao cliente — toda resposta de erro usa uma mensagem genérica e segura definida em `Errors`; o detalhe completo (com stacktrace) fica apenas no log do servidor.

---

## Exemplo completo

```java
// Product.java
public class Product {

    @Id
    private Long id;

    @Updatable
    private String name;

    @Updatable
    private BigDecimal price;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
}

// ProductRepository.java
public class ProductRepository extends ObjectRepository<Product, Long> {
}

// ProductController.java
@Controller("/products")
public class ProductController {

    private final ProductRepository repository;

    public ProductController(ProductRepository repository) {
        this.repository = repository;
    }

    @Get
    public void findAll(HttpRequest request) {
        new HttpResponse()
                .addListObjects("products", new ArrayList<>(repository.findAll()))
                .send(request);
    }

    @Get("/{id}")
    public void findById(HttpRequest request, String id) {

        Product product = repository.findById(Long.parseLong(id));

        if (product == null) {
            HttpUtils.send(Errors.ENTITY_NOT_FOUND, request);
            return;
        }

        new HttpResponse().addObject("product", product).send(request);
    }

    @Post
    public void create(HttpRequest request) {
        Product product = request.getObjectBody(Product.class);
        repository.save(product);
        HttpUtils.ok(request);
    }

    @Put("/{id}")
    public void update(HttpRequest request, String id) {
        Product updated = request.getObjectBody(Product.class);
        repository.update(Long.parseLong(id), updated);
        HttpUtils.ok(request);
    }

    @Delete("/{id}")
    public void delete(HttpRequest request, String id) {
        repository.deleteById(Long.parseLong(id));
        HttpUtils.ok(request);
    }
}

// Main.java
public class Main {

    public static void main(String[] args) {

        RequestServer server = new RequestServer(8080);
        server.setBackLog(1024);

        ProductRepository repository = new ProductRepository();
        ProductController controller = new ProductController(repository);

        server.registerController(controller);
        server.start();
    }
}
```

```bash
curl -X POST localhost:8080/products -d '{"id":1,"name":"Teclado","price":249.90}'
curl localhost:8080/products
curl localhost:8080/products/1
curl -X PUT localhost:8080/products/1 -d '{"name":"Teclado mecânico","price":329.90}'
curl -X DELETE localhost:8080/products/1
```

---

## Estrutura do projeto

```
dev.felippevaz
├── annotations   → @Controller, @Get, @Post, @Put, @Patch, @Delete, @Updatable
├── exceptions    → Errors, GlobalException, ApplicationException
├── handler       → RequestHandler (dispatch + tratamento central de erros)
├── http          → HttpRequest, HttpResponse, HttpAdapter, HttpUtils
├── logging       → LoggingConfig
├── repositories  → ObjectRepository
├── router        → Router, Route, RouteMatch
└── server        → RequestServer
```

---

## Compatibilidade

- Java 8, 11, 17, 21+ e versões mais recentes da JVM
- Sem dependências de container de aplicação — roda como um `main()` simples

---

## Licença

MIT License
