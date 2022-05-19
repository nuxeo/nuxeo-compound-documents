import { Then } from '@cucumber/cucumber';

Then('I can see the {string} {word} tree node is selected', function (title, tab) {
  this.ui.drawer._section(tab).waitForVisible();
  driver.waitUntil(() =>
    this.ui.drawer
      ._section(tab)
      .elements('.content span[class*="compound"].selected a')
      .some((e) => e.getText() === title),
  );
});
