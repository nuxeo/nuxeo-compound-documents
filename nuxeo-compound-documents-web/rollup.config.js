import path from 'path';
import { terser } from 'rollup-plugin-terser';
import copy from 'rollup-plugin-copy';
import minifyHTML from 'rollup-plugin-minify-html-literals';
import resolve from '@rollup/plugin-node-resolve';

// Replace imports by an export of existing globals in Web UI
// https://github.com/nuxeo/nuxeo-web-ui/blob/master/index.js#L7
const GLOBALS = {
  '@polymer/polymer/lib/legacy/class.js': 'const { mixinBehaviors } = Polymer; export { mixinBehaviors };',
  '@polymer/polymer/lib/legacy/polymer.dom.js':
    'const { dom, html, matchesSelector } = Polymer; export { dom, html, matchesSelector };',
  '@polymer/polymer/lib/legacy/polymer-fn.js': 'const { Polymer } = window; export { Polymer };',
  '@polymer/polymer/polymer-element.js': 'const { PolymerElement } = window; export { PolymerElement };',
  '@polymer/polymer/lib/elements/dom-module.js': 'const { DomModule } = window; export { DomModule };',
  '@polymer/polymer/lib/utils/async.js': 'const { timeOut } = Polymer.Async; export { timeOut };',
  '@polymer/polymer/lib/utils/html-tag.js': 'const { html } = Polymer; export { html };',
  '@polymer/polymer/lib/utils/debounce.js':
    'const { enqueueDebouncer, flushDebouncers, Debouncer } = Polymer; ' +
    'export { enqueueDebouncer, flushDebouncers, Debouncer };',
  '@nuxeo/nuxeo-ui-elements/nuxeo-i18n-behavior.js': 'const { I18nBehavior } = Nuxeo; export { I18nBehavior };',
  '@nuxeo/nuxeo-ui-elements/nuxeo-filters-behavior.js':
    'const { FiltersBehavior } = Nuxeo; export { FiltersBehavior };',
  '@nuxeo/nuxeo-ui-elements/nuxeo-format-behavior.js': 'const { FormatBehavior } = Nuxeo; export { FormatBehavior };',
  '@nuxeo/nuxeo-ui-elements/nuxeo-routing-behavior.js':
    'const { RoutingBehavior } = Nuxeo; export { RoutingBehavior };',
};

// Ignore these imports since they should just be all about custom element definitions which are done already by Web UI
const IGNORES = [/^@(nuxeo|polymer)\//];

const TARGET = 'target/classes/web/nuxeo.war/ui';

export default {
  input: 'index.js',
  output: {
    file: `${TARGET}/nuxeo-compound-documents.bundle.js`,
    format: 'es',
  },
  plugins: [
    copy({
      targets: [
        { src: 'document', dest: TARGET },
        { src: 'i18n', dest: TARGET },
        { src: 'search', dest: TARGET },
        { src: 'images', dest: TARGET },
      ],
    }),
    resolve(),
    {
      transform(code, id) {
        // HTML imports
        if (path.extname(id) === '.html') {
          return `export default ${JSON.stringify(code)}`;
        }

        const dep = path.relative('./node_modules', id);

        // Rewrite imports
        if (GLOBALS[dep]) {
          return GLOBALS[dep];
        }

        // Ignore bundled imports
        if (IGNORES.some((r) => r.test(dep))) {
          return 'export default undefined;';
        }

        return code;
      },
    },
    ...(process.env.NODE_ENV === 'production' ? [minifyHTML(), terser()] : []),
  ],
};
