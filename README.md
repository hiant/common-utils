# Common-Utils

A collection of lightweight and reusable Java utilities to streamline common development tasks. This library is designed to be easy to use, with a focus on performance and minimal dependencies.

[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

## Features

- **Cipher**: Simplified AES and RSA encryption/decryption.
- **DateTime**: Type-safe date and time validation and comparison.
- **DelayQueue**: Create `DelayQueue` instances with a specified initial capacity.
- **IP**: Get client IP, local IP, and validate IP addresses.
- **Query**: Simplified database queries with `JdbcTemplate` and dynamic data source support.
- **Quietly**: Close resources and execute code without throwing exceptions.
- **Snowflake**: Generate unique distributed IDs using the Snowflake algorithm.
- **SpEL**: Simplified evaluation of Spring Expression Language (SpEL) expressions.
- **SpringContext**: Static access to the Spring `ApplicationContext`.
- **Thread**: Create and manage thread pools, and propagate MDC context for distributed tracing.

## Installation

To use this library in your project, add the following dependency to your `pom.xml`:

```xml
<dependency>
  <groupId>io.github.hiant</groupId>
  <artifactId>common-utils</artifactId>
  <version>1.0.11</version>
</dependency>
```

## Usage

### CipherUtils

Encrypt and decrypt data using AES:

```java
String secretKey = "your-secret-key";
String iv = "your-iv";
String originalContent = "This is a secret message.";

String encrypted = CipherUtils.encryptWithAES(originalContent, secretKey, iv);
String decrypted = CipherUtils.decryptWithAES(encrypted, secretKey, iv);

System.out.println("Encrypted: " + encrypted);
System.out.println("Decrypted: " + decrypted);
```

### SnowflakeIdUtils

Generate a unique distributed ID:

```java
long id = SnowflakeIdUtils.nextId();
System.out.println("Generated ID: " + id);
```

### ThreadUtils

Create and manage a thread pool:

```java
ThreadPoolExecutor executor = ThreadUtils.newThreadPoolExecutor("my-pool", 5, 10, 100, 60, 5, false);
executor.submit(() -> {
    System.out.println("Executing task in thread pool.");
});
```

## Contributing

Contributions are welcome! If you find a bug or have a feature request, please open an issue. If you want to contribute code, please fork the repository and submit a pull request.

## License

This project is licensed under the Apache License 2.0. See the [LICENSE](LICENSE) file for details.
