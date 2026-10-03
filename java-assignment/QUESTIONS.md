# Questions

Here we have 3 questions related to the code base for you to answer. It is not about right or wrong, but more about what's the reasoning behind your decisions.

1. In this code base, we have some different implementation strategies when it comes to database access layer and manipulation. If you would maintain this code base, would you refactor any of those? Why?

**Answer:**
```txt
	- Unify where persistence logic lives. Store use Panache’s Active Record style, while Product uses a Panache repository.
	- I’d move database operations out of REST resources and consistently use repositories so handlers focus on HTTP concerns.
```
----
2. When it comes to API spec and endpoints handlers, we have an Open API yaml file for the `Warehouse` API from which we generate code, but for the other endpoints - `Product` and `Store` - we just coded directly everything. What would be your thoughts about what are the pros and cons of each approach and what would be your choice?

**Answer:**
```txt
	- **OpenAPI-first (Warehouse):** Makes the API contract explicit and supports consistent documentation, client generation, and contract validation. The tradeoffs are extra specification and code-generation maintenance, a build step, and potential friction when generated models or interfaces don’t fit the implementation cleanly.
	- **Code-first (Product and Store):** Is quick and straightforward for small APIs, and keeps handlers flexible. But the contract is less visible, documentation and clients require more manual work, and API behavior can drift or become inconsistent.
	- **My choice:** Use OpenAPI as the source of truth for APIs that are externally consumed or expected to grow. For this codebase, I’d extend that approach to Product and Store while keeping business logic in handwritten services behind the generated interfaces. That gives the APIs a consistent, testable contract without making generated code responsible for application behavior.
```
----
3. Given the need to balance thorough testing with time and resource constraints, how would you prioritize and implement tests for this project? Which types of tests would you focus on, and how would you ensure test coverage remains effective over time?

**Answer:**
```txt
	- Start with the highest-risk behavior. Add focused unit tests for business logic. Add integration tests for persistence and transactions.
	- Keep coverage useful over time. Run tests in CI, maintain reliable fixtures and isolated test data, and require tests for new or changed business rules.
	- Track coverage as a signal—especially for critical logic—but review whether important outcomes and failure paths are tested rather than relying on a percentage alone.
```