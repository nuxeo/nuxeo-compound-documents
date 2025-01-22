// eslint-disable-next-line import/no-extraneous-dependencies
import { When } from '@cucumber/cucumber';

When('I have a CompoundDocument imported from file {string}', async function (file) {
  return fixtures.documents.import(this.doc, fixtures.blobs.get(file)).then((d) => {
    this.doc = d;
  });
});
