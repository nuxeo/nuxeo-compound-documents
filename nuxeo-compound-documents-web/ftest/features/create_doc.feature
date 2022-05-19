Feature: Import and Create Compound Documents

    I can import and create a Compound Document

  Background:
    Given user "John" exists in group "members"
    And I login as "John"
    And I have a Workspace document
    And I have permission ReadWrite for this document
    And I browse to the document

  @critical
  Scenario: Import a Compound Document file
    When I click the Create Document button
    Then I go to the import tab
    And I can see the import tab content
    And I upload the samplecompound.zip on the tab content page
    When I click the Create button to finish the import
    Then I can see that a document of the type CompoundDocument and title samplecompound is created
    And I see the CompoundDocument page
    And I can see CompoundDocument metadata with the following properties:
      | name         | value                            |
      | title        | samplecompound                   |
    And I can't see the option to add a main blob
