# OpenCDMP RDA maDMP evaluator

**evaluator-rda-madmp** checks a plan's RDA JSON export against the JSON schema of the [RDA DMP Common Standard](https://github.com/RDA-DMP-Common/RDA-DMP-Common-Standard) for machine-actionable Data Management Plans (maDMPs), version 1.2. It reports whether the export is valid and lists every schema violation it finds.

It is an evaluator plugin for [OpenCDMP](https://github.com/OpenCDMP/OpenCDMP), the open platform for Data Management Plans and other Output Management Plans: a user starts an evaluation from a plan, OpenCDMP calls the evaluator over HTTP, and the result appears in OpenCDMP. The service implements the interfaces of [evaluator-base](https://github.com/OpenCDMP/evaluator-base); its evaluator id is `rda-maDMP`. See [Evaluator services](https://opencdmp.github.io/optional-services/evaluator-services/#evaluator-rda-madmp) in the OpenCDMP documentation.

## What it checks

- OpenCDMP sends the plan's RDA JSON export, produced by the [RDA JSON file transformer](https://github.com/OpenCDMP/file-transformer-rda-json). The evaluator reads only that file.
- The file is validated against the maDMP 1.2 JSON schema bundled with the service. The check runs locally and calls no external service.
- The evaluator offers one benchmark, `RDA MADMP Schema Validation`. The plan passes (rank `1`) when the export has no schema violations and fails (rank `0`) otherwise. Each violation becomes one message, for example `#/dmp: required key [dataset] not found`.
- Only plans can be evaluated. Asset evaluation (`/rank/description`) is not supported.

A failure usually means that a field the standard requires is not mapped to the RDA export. The [RDA JSON semantics reference](https://opencdmp.github.io/optional-services/file-transformers/rda-json-semantics/) shows which semantics fill each part of the export.

## Requirements

- An OpenCDMP installation and its Keycloak realm. The service accepts only tokens that this realm issued with the `plugins` audience.
- To build from source: Java 21 and Maven. The service runs on Spring Boot 4.0 and uses `evaluator-base`; the version is in `pom.xml`.

## Run with Docker

The image is [`opencdmp/evaluator-rda-madmp`](https://hub.docker.com/r/opencdmp/evaluator-rda-madmp) on Docker Hub. It listens on port `8080`, reads `app-<PROFILE>.env` from `/config` (set the `PROFILE` environment variable), and writes its logs to `/logs`. [Registering plugin services](https://opencdmp.github.io/deployment/configuration/plugin-services/#step-1-run-the-service) shows a Docker Compose entry.

At startup the service downloads the OpenID configuration from `IDP_ISSUER_URI`, so Keycloak must be reachable at that address. `/actuator/health` reports when the service is up.

## Build from source

```bash
mvn clean package
```

The build produces `web/target/evaluator-rda-maDMP-web-1.0.0-SNAPSHOT.jar`. To run it, set the required variables and start it:

```bash
export IDP_ISSUER_URI=https://opencdmp.example.org/auth/realms/OpenCDMP
export IDP_CLAIMS_ROLES_PATH=plugins.roles
export SECURITY_AUDIENCE=plugins
export LOGGING_PATH=./logs
java -jar web/target/evaluator-rda-maDMP-web-1.0.0-SNAPSHOT.jar
```

Without `SERVER_PORT`, the service listens on port `8083`; check it with `curl http://localhost:8083/actuator/health`. To call it with a token and try it with a local OpenCDMP installation, see [Run and test a plugin locally](https://opencdmp.github.io/developers/plugins/run-locally/).

The `Dockerfile` builds the service from source:

```bash
docker build -t evaluator-rda-madmp .
```

## Configuration

The service reads its settings from environment variables. It needs the [settings common to all plugin services](https://opencdmp.github.io/deployment/configuration/plugin-services/#settings-common-to-all-plugin-services): `IDP_ISSUER_URI`, `IDP_CLAIMS_ROLES_PATH` (`plugins.roles`) and `SECURITY_AUDIENCE` (`plugins`) for authentication, `PROFILE`, and the logging settings. Its own settings are optional:

| Variable | Purpose | Default |
| --- | --- | --- |
| `RDA_MADMP_RDA_SCHEMA` | JSON schema that plans are validated against | `classpath:schema/rda-schema.json` (maDMP 1.2) |
| `RDA_MADMP_HAS_LOGO`, `RDA_MADMP_LOGO_PATH` | Logo shown next to the evaluator in OpenCDMP | `true`, `classpath:rda.png` |
| `RDA_MADMP_USE_SHARED_STORAGE`, `STORAGE_PATH` | Receive the RDA JSON file through a folder shared with OpenCDMP, not in the request body | `false` |

[Evaluator services configuration](https://opencdmp.github.io/deployment/configuration/backend/evaluators/#evaluator-rda-madmp) has the full reference.

### Register with OpenCDMP

Register the service with its address in the API service configuration under `evaluator.sources`, with `evaluatorId: rda-maDMP` and the RDA JSON transformer (`rda-file-transformer`) as `rdaTransformerId`, or for one organisation under **Settings > Plugins**. The API service calls the evaluator with a client-credentials token that carries the `plugins` audience. See [Registering plugin services](https://opencdmp.github.io/deployment/configuration/plugin-services/).

## API

| Method | Path | Description |
| --- | --- | --- |
| `POST` | `/api/evaluator/rank/plan` | Evaluate a plan against the selected benchmarks |
| `POST` | `/api/evaluator/rank/description` | Not supported; returns an error |
| `GET` | `/api/evaluator/config` | Evaluator id, benchmarks, and how ranks map to pass or fail |
| `GET` | `/api/evaluator/logo` | Logo as base64, if one is configured |

The `/api` endpoints require a bearer token. `/actuator/health`, `/actuator/info`, `/actuator/metrics` and `/actuator/prometheus` are open, so keep the port on an internal network.

Request (`file` is the RDA JSON export, base64-encoded):

```json
{
  "planModel": {
    "id": "0b7f4a8e-5d3c-4a51-9f2e-6c1d2b3a4e5f",
    "label": "My research plan",
    "rdaJsonFile": {
      "filename": "plan.json",
      "mimeType": "application/json",
      "file": "<base64-encoded RDA JSON>"
    }
  },
  "benchmarkIds": ["251128td03prl2041aq3pser"]
}
```

The response is a one-element list with the result for the whole plan:

```json
[
  {
    "rank": 0.0,
    "results": [
      {
        "rank": 0.0,
        "benchmarkTitle": "RDA MADMP Schema Validation",
        "metrics": [
          {
            "rank": 0.0,
            "messages": [
              { "title": "Error 1", "message": "#/dmp: required key [dataset] not found" },
              { "title": "Error 2", "message": "#/dmp: required key [language] not found" }
            ]
          }
        ]
      }
    ]
  }
]
```

The [evaluator plugin guide](https://opencdmp.github.io/developers/plugins/evaluator/) and [Common models](https://opencdmp.github.io/developers/plugins/common-models/) describe the request and result models.

## Related resources

- [Evaluator services](https://opencdmp.github.io/optional-services/evaluator-services/#evaluator-rda-madmp): what each OpenCDMP evaluator checks
- [Evaluating plans](https://opencdmp.github.io/using/plans/evaluators/): running an evaluation and reading the result in OpenCDMP
- [Evaluator plugin guide](https://opencdmp.github.io/developers/plugins/evaluator/): building your own evaluator
- [RDA DMP Common Standard](https://github.com/RDA-DMP-Common/RDA-DMP-Common-Standard): the standard and its JSON schema
- [OpenCDMP documentation](https://opencdmp.github.io/)

## Contributing

Report bugs and suggest changes in [GitHub Issues](https://github.com/OpenCDMP/evaluator-rda-madmp/issues). See [Contributing](https://opencdmp.github.io/developers/contributing/) for how to propose a change.

## License

evaluator-rda-madmp is licensed under the [European Union Public Licence (EUPL) v1.2](LICENSE).

## Contact

Questions: email opencdmp at cite.gr.
