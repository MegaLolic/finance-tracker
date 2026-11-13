# Account and money rules

- Account creation requires an initial balance and currency. Negative balances are allowed.
- Account updates require the current version, balance and currency, and may change the name only.
- A different balance or currency in an account update returns HTTP 409. Change the balance through transactions; currency conversion is not supported.
- Numerically equal balances such as `100.0` and `100.00` are accepted.
- Account balances and transaction amounts accept at most 17 integer digits and 2 fractional digits, matching PostgreSQL `DECIMAL(19, 2)`. Invalid inputs return HTTP 400 rather than being rounded by the database.
- Transactions still require a strictly positive amount; the category determines whether it increases or decreases the balance.

Validation uses the existing controller validation and DTO constraints. Business rules remain in `AccountService`, and the existing exception advice maps `BusinessException` to HTTP 409.

References: [Jakarta Validation Digits](https://jakarta.ee/specifications/bean-validation/3.1/apidocs/jakarta/validation/constraints/digits), [Spring MVC request body validation](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/requestbody.html).
