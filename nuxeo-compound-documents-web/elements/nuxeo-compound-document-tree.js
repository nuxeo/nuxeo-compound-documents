/**
@license
(C) Copyright Nuxeo Corp. (http://nuxeo.com/)

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
import '@polymer/iron-icon/iron-icon.js';
import '@polymer/iron-flex-layout/iron-flex-layout-classes.js';
import '@polymer/paper-spinner/paper-spinner.js';
import { Debouncer } from '@polymer/polymer/lib/utils/debounce.js';
import { timeOut } from '@polymer/polymer/lib/utils/async.js';
import '@nuxeo/nuxeo-elements/nuxeo-document.js';
import '@nuxeo/nuxeo-elements/nuxeo-page-provider.js';
import { RoutingBehavior } from '@nuxeo/nuxeo-ui-elements/nuxeo-routing-behavior.js';
import { FiltersBehavior } from '@nuxeo/nuxeo-ui-elements/nuxeo-filters-behavior.js';
import '@nuxeo/nuxeo-ui-elements/nuxeo-tree/nuxeo-tree.js';
import { Polymer } from '@polymer/polymer/lib/legacy/polymer-fn.js';
import { html } from '@polymer/polymer/lib/utils/html-tag.js';
import { I18nBehavior } from '@nuxeo/nuxeo-ui-elements/nuxeo-i18n-behavior.js';

/**
`nuxeo-compound-document-tree`
@group Nuxeo UI
@element nuxeo-compound-document-tree
*/
Polymer({
  _template: html`
    <style include="iron-flex iron-positioning nuxeo-styles">
      :host {
        display: block;
        --nuxeo-tree-theme: {
          padding: 1em;
          color: var(--nuxeo-drawer-text);
        }
        --nuxeo-tree-node-theme: {
          min-height: 24px;
        }
        --nuxeo-tree-children-theme: {
          padding-left: 1em;
        }
        --nuxeo-tree-node-more-theme: {
          line-height: 1.3em;
          display: inline-block;
          vertical-align: text-top;
          margin-left: 1.3em;
          word-break: break-word;
        }
      }

      .content {
        padding: 5px 0;
        overflow: auto;
        height: calc(100vh - 72px - (var(--nuxeo-app-top, 0) + var(--nuxeo-app-bottom, 0)));
      }

      .node-name {
        line-height: 1.3em;
        display: inline-block;
        vertical-align: text-top;
        margin-left: 1.3em;
        word-break: break-word;
      }

      a {
        @apply --nuxeo-link;
      }

      a:hover {
        @apply --nuxeo-link-hover-color;
      }

      #root a,
      a:active,
      a:visited,
      a:focus {
        color: var(--nuxeo-drawer-text);
      }

      iron-icon {
        opacity: 0.3;
        width: 1.3rem;
        margin-right: -1.6em;
        margin-top: -0.07rem;
      }

      [toggle] {
        cursor: pointer;
      }

      .parents {
        line-height: 1.5em;
      }

      .parents + nuxeo-tree {
        padding: 6px 5px;
      }

      .parents > nuxeo-tree {
        padding: 4px 5px;
      }

      .parents a {
        @apply --layout-horizontal;
        padding: 0.35em;
        color: var(--nuxeo-drawer-text);
        border-bottom: 1px solid var(--nuxeo-border);
      }

      .parents span {
        text-overflow: ellipsis;
        overflow: hidden;
        white-space: nowrap;
        display: block;
        min-width: 1.3em;
      }

      .parent {
        padding: 0.12em 0 0;
      }

      paper-spinner {
        height: 1.1rem;
        width: 1.1rem;
        margin-right: -1.4em;
      }

      .noPermission {
        opacity: 0.5;
        font-weight: 300;
        padding: 1.5em 0.7em;
        text-align: center;
        font-size: 1.1rem;
      }

      .header h5 {
        margin: 0;
      }

      .item.compoundChild {
        display: table;
        padding-left: 0.35em;
      }

      .item.compoundChild {
        padding-top: 8px;
      }

      .item.compoundChild .node-name {
        margin-left: 8px;
        display: table-cell;
        vertical-align: middle;
      }

      .item.compound.selected .node-name,
      .item.compoundChild.selected .node-name {
        border-bottom: 1px solid var(--nuxeo-primary-color);
      }

      img {
        height: 24px;
        display: table-cell;
        vertical-align: middle;
      }
    </style>

    <nuxeo-document
      id="doc"
      doc-path="[[docPath]]"
      response="{{document}}"
      enrichers="hasFolderishChild, hasContent, thumbnail"
    ></nuxeo-document>

    <nuxeo-page-provider id="children"> </nuxeo-page-provider>

    <div class="header" hidden$="[[!label]]">
      <h5>[[i18n(label)]]</h5>
    </div>

    <div class="content" role="tree">
      <div class="parents" hidden$="[[_noPermission]]">
        <a href$="[[urlFor('document', '/')]]" class="layout horizontal" hidden$="[[_hideRoot(document)]]">
          <span aria-hidden="true"><iron-icon icon="icons:chevron-left"></iron-icon></span>
          <span class="parent">[[i18n('browse.root')]]</span>
        </a>
        <template is="dom-repeat" items="[[parents]]" as="item">
          <a href$="[[urlFor(item)]]">
            <span><iron-icon icon="icons:chevron-left"></iron-icon></span>
            <span class="parent">[[item.title]]</span>
          </a>
        </template>
      </div>
      <nuxeo-tree id="tree" data="[[document]]" controller="[[controller]]" node-key="uid">
        <template class="horizontal layout">
          <span role="treeitem" aria-expanded="[[opened]]" class$="[[_computeItemClass(item)]]">
            <template class="flex" is="dom-if" if="[[!isLeaf]]">
              <paper-spinner active$="[[loading]]" aria-hidden="true"></paper-spinner>
              <iron-icon icon="[[_expandIcon(opened)]]" toggle hidden$="[[loading]]" aria-hidden="true"></iron-icon>
            </template>
            <template class="flex" is="dom-if" if="[[_isCompoundChild(item)]]">
              <img src="[[_thumbnail(item)]]" alt$="[[item.title]]" />
            </template>
            <span class="node-name flex">
              <a href$="[[urlFor(item)]]">[[_title(item)]]</a>
            </span>
          </span>
        </template>
      </nuxeo-tree>
      <div class="noPermission" hidden$="[[!_noPermission]]">[[i18n('browse.tree.noDocument')]]</div>
    </div>
  `,

  is: 'nuxeo-compound-document-tree',
  behaviors: [RoutingBehavior, I18nBehavior, FiltersBehavior],

  properties: {
    controller: Object,
    auto: {
      type: Boolean,
      value: false,
    },
    rootDocPath: {
      type: String,
      value: '/',
      observer: '_rootDocPathChanged',
    },
    docPath: {
      type: String,
      value: '/',
    },
    document: {
      type: Object,
      observer: '_documentChanged',
    },
    currentDocument: {
      type: Object,
      observer: '_currentDocumentChanged',
    },
    parents: {
      type: Array,
      value: [],
    },
    label: String,
    visible: {
      type: Boolean,
    },
    cannotSee: {
      type: Boolean,
      value: false,
    },
    _noPermission: {
      type: Boolean,
      value: false,
    },
  },

  observers: ['_fetchDocument(docPath, visible)'],

  ready() {
    window.addEventListener('nuxeo-documents-deleted', (e) => {
      if (e.detail.documents) {
        this.removeDocuments(e.detail.documents);
        return;
      }
      // when in select all mode we don't have a list of documents in the event detail
      this._fetchDocument();
    });
    window.addEventListener('document-deleted', (e) => {
      const { doc } = e.detail;
      // check if we are deleting a compound child or any document inside the compound
      if (this._isCompoundChild(doc) || (doc && this.document && this.document.uid !== doc.uid)) {
        this.removeDocuments([doc]);
        return;
      }
      // if for any reason we don't have a document, we should fetch the current one at least
      this._fetchDocument();
    });

    window.addEventListener('refresh-display', this._fetchDocument.bind(this));

    window.addEventListener('document-created', this._fetchDocument.bind(this));

    this.controller = {
      getChildren: function _(node, page) {
        if (this._isCompound(node)) {
          this.$.children.provider = 'advanced_document_content';
          this.$.children.enrichers = 'hasFolderishChild, hasContent, thumbnail';
          this.$.children.params = { ecm_parentId: node.uid, ecm_trashed: false };
        } else {
          this.$.children.provider = 'tree_children';
          this.$.children.enrichers = 'hasFolderishChild, hasContent';
          this.$.children.params = [node.uid];
        }
        this.$.children.page = page;
        return this.$.children.fetch().then((data) => {
          this._expandCurrentDocument();
          return {
            items: data.entries,
            isNextAvailable: this.$.children.isNextPageAvailable,
          };
        });
      }.bind(this),

      isLeaf: function _(node) {
        const hasChildren = this._isCompound(node)
          ? node.contextParameters && node.contextParameters.hasContent
          : node.contextParameters && node.contextParameters.hasFolderishChild;
        return !hasChildren;
      }.bind(this),
    };
  },

  _hideRoot(doc) {
    return this.rootDocPath !== '/' || (doc && doc.type && doc.type === 'Root');
  },

  _fetchDocument() {
    if (this.visible && this.docPath) {
      this.__fetchDebouncer = Debouncer.debounce(this.__fetchDebouncer, timeOut.after(150), () => {
        this._noPermission = false;
        this.$.doc.execute().catch((err) => {
          if (err && err.status === 403) {
            this._noPermission = true;
          } else {
            throw err;
          }
        });
      });
    }
  },

  _currentDocumentChanged() {
    const doc = this.currentDocument;
    if (doc && doc.path && doc.path.startsWith(this.rootDocPath)) {
      if (this.docPath === doc.path && this.document && this.document.title !== doc.title) {
        // If document is the same as before but its name changed, get the document again
        this.$.doc.get();
      }

      if (this.docPath !== doc.path && !this.hasFacet(doc, 'HiddenInNavigation')) {
        this.$.tree.style.display = 'none';
        this.parents = [];

        if (doc.type === 'Root') {
          this.docPath = doc.path;
          return;
        }

        const { entries } = doc.contextParameters.breadcrumb;

        let stopIdx = entries.findIndex((e) => this._isCompound(e));
        if (stopIdx === -1) {
          stopIdx = entries.length - 1;
        }

        if (this.docPath !== entries[stopIdx].path) {
          this.docPath = entries[stopIdx].path;
        } else {
          // XXX the document won't be updated, so we'll just call the method to change visibility
          this._documentChanged();
        }

        for (let i = 0; i < stopIdx; i += 1) {
          const entry = entries[i];
          if (!this.hasFacet(entry, 'HiddenInNavigation') && entry.path.startsWith(this.rootDocPath)) {
            this.push('parents', entry);
          }
        }
        this._expandCurrentDocument();
      } else if (this._isCompound(doc)) {
        // XXX force the styles inside the tree to update
        this._updateTreeStyles();
      }
    }
  },

  _expandCurrentDocument() {
    this.__expandDebouncer = Debouncer.debounce(this.__expandDebouncer, timeOut.after(100), async () => {
      let parents =
        this.currentDocument &&
        this.currentDocument.contextParameters &&
        this.currentDocument.contextParameters.breadcrumb &&
        this.currentDocument.contextParameters.breadcrumb.entries;
      if (!parents) {
        return; // if we are missing parent information, do nothing
      }
      parents = this.currentDocument.contextParameters.breadcrumb.entries.filter(
        (d) => !this.parents.find((a) => a.uid === d.uid),
      );
      const uids = [...parents.map((doc) => doc.uid)];
      await customElements.whenDefined('nuxeo-tree');
      this.$.tree.open(...uids);
      this._updateTreeStyles();
    });
  },

  _documentChanged() {
    if (this.document && this.hasFacet(this.document, 'Folderish')) {
      this.$.tree.style.display = 'block';
    }
  },

  _rootDocPathChanged() {
    this.docPath = this.rootDocPath;
  },

  _expandIcon(opened) {
    return `hardware:keyboard-arrow-${opened ? 'down' : 'right'}`;
  },

  _icon(opened) {
    return opened ? 'icons:folder-open' : 'icons:folder';
  },

  _title(item) {
    return item.type === 'Root' ? this.i18n('browse.root') : item.title;
  },

  removeDocuments(documents) {
    const uids = documents.map((doc) => doc.uid);
    this.$.tree.removeNodes(uids);
  },

  _thumbnail(doc) {
    return doc &&
      doc.uid &&
      doc.contextParameters &&
      doc.contextParameters.thumbnail &&
      doc.contextParameters.thumbnail.url
      ? doc.contextParameters.thumbnail.url
      : '';
  },

  _isCompound(doc) {
    return (
      this.hasFacet(doc, 'CompoundDocument') ||
      (doc && this.hasFacet(doc, 'Folderish') && doc.type === 'CompoundDocumentFolder')
    );
  },

  _isCompoundChild(doc) {
    // TODO improve this
    return !this.hasFacet(doc, 'Folderish') && !!this._thumbnail(doc);
  },

  _computeItemClass(item) {
    let c = 'item';
    if (this._isCompound(item)) {
      c += ' compound';
    }
    if (this._isCompoundChild(item)) {
      c += ' compoundChild';
    }
    return c;
  },

  _updateTreeStyles() {
    // this.$.tree._update(); // XXX this causes the three to flicker, when we just want to update the styles
    Array.from(this.$.tree.querySelectorAll('nuxeo-tree-node')).forEach((node) => {
      const item = node.querySelector('#content .item');
      const uid = node.getAttribute('data-uid');
      if (uid === this.currentDocument.uid && !item.classList.contains('selected')) {
        item.classList.add('selected');
      } else if (uid !== this.currentDocument.uid && item.classList.contains('selected')) {
        item.classList.remove('selected');
      }
    });
  },
});
