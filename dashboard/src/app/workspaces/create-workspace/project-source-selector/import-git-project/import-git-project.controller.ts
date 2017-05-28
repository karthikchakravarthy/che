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

import {ImportGitProjectService} from './import-git-project.service';
import {ProjectSourceSelectorService} from '../project-source-selector.service';

/**
 * This class is handling the controller for the Git project import.
 *
 * @author Oleksii Kurinnyi
 */
export class ImportGitProjectController {
  /**
   * Import Git project service.
   */
  private importGitProjectService: ImportGitProjectService;
  /**
   * Project source selector service.
   */
  private projectSourceSelectorService: ProjectSourceSelectorService;
  /**
   * Git repository location.
   */
  private location: string;

  /**
   * Default constructor that is using resource injection
   * @ngInject for Dependency injection
   */
  constructor(importGitProjectService: ImportGitProjectService, projectSourceSelectorService: ProjectSourceSelectorService) {
    this.importGitProjectService = importGitProjectService;
    this.projectSourceSelectorService = projectSourceSelectorService;

    this.location = this.importGitProjectService.location;

    this.projectSourceSelectorService.subscribe(this.clearField.bind(this));
  }

  clearField(projectTemplateName: string): void {
    const re = new RegExp('/' + projectTemplateName + '.git');
    if (!re.test(this.location)) {
      return;
    }

    this.location = '';

    this.onChanged();
  }

  /**
   * Callback which is called when location is changed.
   */
  onChanged(): void {
    this.importGitProjectService.onLocationChanged(this.location);
  }

}
