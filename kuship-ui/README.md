# kuship-ui

kuship console frontend.

> Forked from [rainbond-ui](https://github.com/goodrain/rainbond-ui) at commit
> `9dcc296d3ec5d6cfdfc1c351bb0e50d1af0ac126` and evolves independently from now on.
> The original implementation is kept read-only at `reference/rainbond-ui/` (git submodule).

[Rainbond website](http://www.rainbond.com) • [Rainbond docs](https://www.rainbond.com/docs/)

See [README.zh-CN.md](./README.zh-CN.md) for the full Chinese documentation.

## Backend target

The dev server proxies five paths (`/console`, `/data`, `/openapi/v1`,
`/enterprise-server`, `/app-server`) to a single backend, controlled by the
`CONSOLE_PROXY_TARGET` environment variable in `config/config.js`.

- **Default** (`yarn start` with no env var): `http://localhost:7070` — the
  rainbond-console test instance started by this repo's
  `add-docker-compose-stack` change.
- **Switch to kuship-console** (Java Spring Boot rewrite, in progress):
  `CONSOLE_PROXY_TARGET=http://localhost:<kuship-console-port> yarn start`.
  The exact port will be set by the kuship-console rollout change.
