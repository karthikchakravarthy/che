/*
 * Copyright (c) 2015-2017 Codenvy, S.A.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Codenvy, S.A. - initial API and implementation
 */
'use strict';

import {ImportZipProjectService} from './import-zip-project.service';
import {ProjectSourceSelectorService} from '../project-source-selector.service';

/**
 * This class is handling the controller for the Zip project import.
 *
 * @author Oleksii Kurinnyi
 */
export class ImportZipProjectController {
  /**
   * Import Zip project service.
   */
  private importZipProjectService: ImportZipProjectService;
  /**
   * Project source selector service.
   */
  private projectSourceSelectorService: ProjectSourceSelectorService;
  /**
   * Zip repository location.
   */
  private location: string;
  /**
   * Skip the root folder of archive if <code>true</code>
   */
  private skipFirstLevel: boolean;

  /**
   * Default constructor that is using resource injection
   * @ngInject for Dependency injection
   */
  constructor(importZipProjectService: ImportZipProjectService, projectSourceSelectorService: ProjectSourceSelectorService) {
    this.importZipProjectService = importZipProjectService;
    this.projectSourceSelectorService = projectSourceSelectorService;

    this.location = this.importZipProjectService.location;
    this.skipFirstLevel = this.importZipProjectService.skipFirstLevel;

    this.projectSourceSelectorService.subscribe(this.clearFields.bind(this));
  }

  clearFields(projectTemplateName: string): void {
    const re = new RegExp('/' + projectTemplateName + '.zip');
    if (!re.test(this.location)) {
      return;
    }

    this.location = '';
    this.skipFirstLevel = false;

    this.onChanged();
  }

  /**
   * Callback which is called when location or source parameter is changed.
   */
  onChanged(): void {
    this.importZipProjectService.onChanged(this.location, this.skipFirstLevel);
  }
}
