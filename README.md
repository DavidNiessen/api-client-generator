# API Client Generator
#### This project automatically generates and publishes api-clients from OpenAPI specifications.

### Add new client
1. Go to /api/
2. Create a new folder with the name of the client you want to add
3. Create an openapi.yaml/json containing the schema
4. Create a config.json

### Generate API clients 
> ./gradlew build

### Publish to GitHub Packages
> ./gradlew publishAllGeneratedClients