Feature: Browse to the Compound Document

    I can open and browse a Compound Document

  Background:
    Given user "John" exists in group "members"
    And I login as "John"
    And I have a Workspace document
    And I have permission ReadWrite for this document
    And I browse to the document
    And I have a CompoundDocument imported from file "samplecompound.zip"
    And I have permission Read for this document
    And I browse to the document with path "/default-domain/my_document"

  Scenario: Compound document folders come after non-folderish children
    When I click the "browser" button
    Then I can see the browser tree
    And I can see the "samplecompound" browser tree node
    When I click "samplecompound" in the browser tree
    Then I can see the "samplecompound" browser tree node is selected
    And I can see the "picture2.png" child node at position 1
    And I can see the "picture1.png" child node at position 2
    And I can see the "folder" child node at position 3

  Scenario: Browse the tree to a compound document
    When I click the "browser" button
    Then I can see the browser tree
    And I can see the "samplecompound" browser tree node
    When I click "samplecompound" in the browser tree
    Then I can see the "samplecompound" browser tree node is selected
    And I see the CompoundDocument page
    And I can see CompoundDocument metadata with the following properties:
      | name  | value          |
      | title | samplecompound |
    And I can see the "picture1.png" browser tree node
    And I can see the "picture2.png" browser tree node
    And I can see the "folder" browser tree node

  Scenario: Browse to a compound document
    When I browse to the document with path "/default-domain/my_document/samplecompound"
    And I click the "browser" button
    Then I can see the browser tree
    And I can see the "samplecompound" browser tree node is selected
    And I see the CompoundDocument page
    And I can see CompoundDocument metadata with the following properties:
      | name  | value          |
      | title | samplecompound |
    Then I can see the "picture1.png" browser tree node
    And I can see the "picture2.png" browser tree node
    And I can see the "folder" browser tree node

Scenario: Browse to a compound document folder inside a compound
    When I browse to the document with path "/default-domain/my_document/samplecompound/folder"
    Then I see the CompoundDocumentFolder page
    And I can see CompoundDocumentFolder metadata with the following properties:
      | name  | value  |
      | title | folder |
    And I can see 1 documents
    When I click the "browser" button
    Then I can see the browser tree
    And I can see the "folder" browser tree node is selected

Scenario: Browse to a compound child document
    When I browse to the document with path "/default-domain/my_document/samplecompound/picture1.png"
    Then I see the Picture page
    And I can see Picture metadata with the following properties:
      | name  | value        |
      | title | picture1.png |
    When I click the "browser" button
    Then I can see the browser tree
    And I can see the "picture1.png" browser tree node is selected
