# Nuxeo Compound Documents

## Definition

We can define Compound Documents as files that only make sense as a group of interrelated children, possibly at several levels, each of these is modifiable.


## Build

Nuxeo's ecosystem is Java based and uses Maven. This addon is not an exception and can be built by simply performing:

```shell script
mvn clean install
```

The following configuration is needed in `$HOME/.npmrc`
```
@nuxeo:registry=https://packages.nuxeo.com/repository/npm-public/
```
to build the **nuxeo-compound-documents-web** module.