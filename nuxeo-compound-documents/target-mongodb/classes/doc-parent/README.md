# Nuxeo Compound Documents

## Definition

We can define Compound Documents as files that only make sense as a group of interrelated children, possibly at several levels, each of these is modifiable.


## Build

Nuxeo's ecosystem is Java based and uses Maven. This addon is not an exception and can be built by simply performing:

```shell script
mvn clean install
```

## Package Management

This project uses `package-lock.json` to ensure consistent dependency versions across all environments. When installing npm packages:

- **For CI environments and developers (no dependency changes)**: Use `npm ci`. This ensures the exact versions from `package-lock.json` are installed, providing faster and more reliable builds.
- **When adding or updating dependencies**: Use `npm install <pkg>` (or `npm install` after editing `package.json`) to update both `package.json` and `package-lock.json`, then commit the updated `package-lock.json`.
- **Security updates**: Dependabot will automatically create PRs to update `package-lock.json` for security or version updates.