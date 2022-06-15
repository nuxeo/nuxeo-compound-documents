Feature: Check the preview of a Compound Document

    I can see the preview of a Compound Document

  Scenario: Check the preview of a compound document
    Given user "John" exists in group "members"
    And I login as "John"
    And I have a Workspace document
    And I have permission ReadWrite for this document
    And I have a CompoundDocument imported from file "samplecompound.zip"
    And I have permission Read for this document
    When I browse to the document with path "/default-domain/my_document/samplecompound"
    And I click the "browser" button
    Then I can see the browser tree
    And I can see the "samplecompound" browser tree node is selected
    And I see the CompoundDocument page
    And I can see the inline nuxeo-image-viewer previewer
    When I browse to the document with path "/default-domain/my_document/samplecompound/preview.png"
    Then I see the Picture page
    And I can see the inline nuxeo-image-viewer previewer

