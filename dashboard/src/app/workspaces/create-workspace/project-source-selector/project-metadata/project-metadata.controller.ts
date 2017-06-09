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

import {ProjectMetadataService} from './project-metadata.service';
import {ProjectSourceSelectorService} from '../project-source-selector.service';

/**
 * This class is handling the controller for project's metadata.
 *
 * @author Oleksii Kurinnyi
 */
export class ProjectMetadataController {
  /**
   * Project metadata service.
   */
  private projectMetadataService: ProjectMetadataService;
  /**
   * Project's source selector service.
   */
  private projectSourceSelectorService: ProjectSourceSelectorService;
  /**
   * The project's template.
   */
  private template: che.IProjectTemplate;
  /**
   * Original template name;
   */
  private templateName: string;

  /**
   * Default constructor that is using resource injection
   * @ngInject for Dependency injection
   */
  constructor(projectMetadataService: ProjectMetadataService, projectSourceSelectorService: ProjectSourceSelectorService) {
    this.projectMetadataService = projectMetadataService;
    this.projectSourceSelectorService = projectSourceSelectorService;

    this.templateName = this.template.name;
  }

  /**
   * Callback which is called when metadata is changed.
   */
  onMetadataChanged(): void {
    this.projectMetadataService.onMetadataChanged(this.template);
  }

  /**
   * Returns <code>true</code> if name is unique.
   *
   * @param {string} name new project's name
   * @return {boolean}
   */
  isNameUnique(name: string): boolean {
    return this.projectSourceSelectorService.isProjectTemplateNameUnique(name, this.templateName);
  }

}
