import { Then } from '@cucumber/cucumber';

Then('I can see the {string} browser tree node is selected', function (title) {
  this.ui.drawer._section('browser').waitForVisible();
  driver.waitUntil(() =>
    this.ui.drawer
      ._section('browser')
      .elements('.content .selected a')
      .some((e) => e.getText() === title),
  );
});

Then('I can see the {string} child node at position {int}', function (title, pos) {
  this.ui.drawer._section('browser').waitForVisible();
  driver.waitUntil(
    () =>
      this.ui.drawer
        ._section('browser')
        .elements('nuxeo-tree-node')
        .findIndex((el) => el.getText() === title) === pos,
  );
});
