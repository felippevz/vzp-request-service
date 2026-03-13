# 🌐 Request Service

**VZP Request Service** é um framework HTTP leve para Java baseado em `HttpsServer`.

Ele foi criado para oferecer uma alternativa **simples e com inicialização extremamente rápida** em comparação com frameworks mais pesados como Spring Boot.

O objetivo principal é atender **APIs pequenas ou serviços internos com baixo volume de requisições**, mantendo o consumo de memória baixo e a configuração simples.

O projeto foi desenvolvido para funcionar desde **Java 8 até as versões mais modernas do Java**.

---

## Características

* Inicialização rápida
* Baixo consumo de memória
* Baseado em `HttpsServer` da própria JDK
* Controllers baseados em anotações
* Mapeamento automático de rotas
* Suporte a JSON
* Integração com JPA / Hibernate
* Banco embarcado com H2
* Scanning automático de classes
* Compatível com **Java 8 até versões atuais**

---

## Instalação

O projeto é distribuído através do **JitPack**.

Adicione o repositório:

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>
```

Adicione a dependência:

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

## Inicialização do servidor

Exemplo básico de inicialização do servidor:

```java
//Criando objeto RequestServer com porta 8080
RequestServer server = new RequestServer(8080);

//Configurações do servidor  
server.setExecutor(Executors.newFixedThreadPool(15)); //<-- Use qualquer Executor (default: newFixedThreadPool(1))
server.setBackLog(1024); //<-- BackLog de requisições (default: 0)
server.setHttpsConfigurator(new HttpsConfigurator(SSLContext)); //<-- Use suas configurações Https

//Banco de dados 
PersistenceManagerFactory.init(properties); //<-- Adicionar as propriedades de um banco (default: H2)

//Criando controller
ProductRepository repository = new ProductRepository();
ProductService service = new ProductService(repository);
ProductController controller = new ProductController(service);

//Registrando Controller
server.registerController(controller);

//Iniciando servidor
server.start();
```

### Veja um CRUD simples em:

-> [CRUD request service](https://github.com/felippevz/vzp-request-service-crud)

---

## Controllers

Os controllers são definidos utilizando a anotação `@Controller`.

```java
@Controller("/generic")
public class genericController {

}
```

Essa anotação define o **endpoint base** do controller.

---

## Métodos HTTP suportados

Os endpoints podem ser definidos utilizando anotações:

* `@Get`
* `@Post`
* `@Put`
* `@Patch`
* `@Delete`

---

## Exemplos de endpoints

### Criar recurso

```java
@Post
public void create(HttpRequest request) {

}
```

### Buscar por ID

```java
@Get("/{id}")
public void read(HttpRequest request, String id) {

}
```

O framework resolve automaticamente parâmetros de rota como `{id}`.

---

## Camada de repositório

A persistência pode ser implementada estendendo `ObjectRepository`.

```java
public class ProductRepository extends ObjectRepository<Product, Long> {

}
```

Esse repositório genérico permite implementar facilmente operações de banco de dados utilizando **Hibernate / JPA**.

---

## Estrutura recomendada

A arquitetura sugerida segue um padrão simples em camadas:

```
Controller
   ↓
Service
   ↓
Repository
   ↓
Banco de dados
```

Exemplo:

```
ProductController
ProductService
ProductRepository
Product (model)
```

---

## Casos de uso

O VZP Request Service é ideal para:

* APIs internas
* microserviços simples
* ferramentas administrativas
* automações
* serviços com **baixo volume de requisições**
* aplicações onde **Spring Boot seria excessivo**

---

## Compatibilidade Java

O projeto foi desenvolvido para funcionar em:

* Java 8
* Java 11
* Java 17
* Java 21+
* versões mais recentes da JVM

---

## 🚀 Tecnologias utilizadas

Internamente o projeto utiliza as seguintes bibliotecas:

```xml
<dependencies>
    <dependency>
        <groupId>com.google.code.gson</groupId>
        <artifactId>gson</artifactId>
        <version>2.10.1</version>
    </dependency>

    <dependency>
        <groupId>jakarta.persistence</groupId>
        <artifactId>jakarta.persistence-api</artifactId>
        <version>2.2.3</version>
    </dependency>

    <dependency>
        <groupId>org.hibernate</groupId>
        <artifactId>hibernate-core</artifactId>
        <version>5.6.15.Final</version>
    </dependency>

    <dependency>
        <groupId>io.github.classgraph</groupId>
        <artifactId>classgraph</artifactId>
        <version>4.8.165</version>
    </dependency>

    <dependency>
        <groupId>com.h2database</groupId>
        <artifactId>h2</artifactId>
        <version>2.2.220</version>
    </dependency>
</dependencies>
```



# Licença

MIT License
