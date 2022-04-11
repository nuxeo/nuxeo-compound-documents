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
import { fixture, flush, html } from '@nuxeo/testing-helpers';
import { expect } from '@esm-bundle/chai';

suite('Compound Documents', () => {
  suite('Compound element example', () => {
    let text;

    setup(() => {
      text = 'Dummy string';
    });

    teardown(() => {});

    suite('Test input', () => {
      test('Assert input value', async () => {
        const input = await fixture(html`<input type="text" value=${text} />`);

        await flush();
        expect(input.value).to.be.equal('Dummy string');
      });
    });
  });
});
