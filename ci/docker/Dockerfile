ARG NUXEO_VERSION=2025

FROM docker-private.packages.nuxeo.com/nuxeo/nuxeo:${NUXEO_VERSION}

ARG BUILD_TAG=unknown
ARG SCM_REF=unknown
ARG VERSION=unknown

LABEL org.nuxeo.compound.documents.build-tag=$BUILD_TAG
LABEL org.nuxeo.compound.documents.scm-ref=$SCM_REF
LABEL org.nuxeo.compound.documents.version=$VERSION

COPY --chown=900:0 target/nuxeo-compound-documents-package-$VERSION.zip /packages/
