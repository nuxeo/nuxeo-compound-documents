// eslint-disable-next-line import/no-extraneous-dependencies
import { Given } from '@cucumber/cucumber';

Given(/^I have a CompoundDocument imported from file "(.+)"$/, function (file) {
  return fixtures.documents.import(this.doc, fixtures.blobs.get(file)).then((d) => {
    this.doc = d;
  });
});
