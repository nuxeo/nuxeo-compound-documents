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

- **For CI environments**: Use `npm ci`. This ensures the exact versions from `package-lock.json` are installed, providing faster and more reliable builds.
- **For local development**:
    - Use `npm install <pkg>` when adding, upgrading, or removing dependencies so that `package-lock.json` is updated accordingly. Commit the updated `package-lock.json` along with your changes.
    - Use `npm ci` when you want a clean reinstall that exactly matches the current `package-lock.json`.
- **Security updates**: Dependabot will automatically create PRs to update `package-lock.json` for security or version updates.