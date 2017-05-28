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

import {ProjectSource} from '../project-source.enum';
import {TemplateSelectorSvc} from './template-selector.service';
import {StackSelectorSvc} from '../../stack-selector/stack-selector.service';
import {ProjectSourceSelectorService} from '../project-source-selector.service';

export class TemplateSelectorController {
  /**
   * Filter service.
   */
  private $filter: ng.IFilterService;
  /**
   * Project source selector service.
   */
  private projectSourceSelectorService: ProjectSourceSelectorService;
  /**
   * Template selector service.
   */
  private templateSelectorSvc: TemplateSelectorSvc;
  /**
   * Stack selector service.
   */
  private stackSelectorSvc: StackSelectorSvc;
  /**
   * Helper for lists.
   */
  private cheListHelper: che.widget.ICheListHelper;
  /**
   * The list of tags of selected stack.
   */
  private stackTags: string[];
  /**
   * Sorted list of all templates.
   */
  private allTemplates: Array<che.IProjectTemplate>;
  /**
   * Filtered and sorted list of templates.
   */
  private filteredTemplates: Array<che.IProjectTemplate>;
  /**
   * List of selected templates.
   */
  private selectedTemplates: Array<che.IProjectTemplate>;
  /**
   * Number of templates selected to be added to the list of ready-to-import projects.
   */
  private newTemplatesNumber: number;

  /**
   * Default constructor that is using resource injection
   * @ngInject for Dependency injection
   */
  constructor($filter: ng.IFilterService, $scope: ng.IScope, projectSourceSelectorService: ProjectSourceSelectorService, templateSelectorSvc: TemplateSelectorSvc, stackSelectorSvc: StackSelectorSvc, cheListHelperFactory: che.widget.ICheListHelperFactory) {
    this.$filter = $filter;
    this.projectSourceSelectorService = projectSourceSelectorService;
    this.templateSelectorSvc = templateSelectorSvc;
    this.stackSelectorSvc = stackSelectorSvc;

    const helperId = 'template-selector';
    this.cheListHelper = cheListHelperFactory.getHelper(helperId);
    $scope.$on('$destroy', () => {
      cheListHelperFactory.removeHelper(helperId);
    });

    this.allTemplates = [];
    this.filteredTemplates = [];
    this.selectedTemplates = this.templateSelectorSvc.getTemplates();

    this.onStackChanged();
    this.stackSelectorSvc.subscribe(this.onStackChanged.bind(this));

    this.templateSelectorSvc.fetchTemplates().then(() => {
      this.allTemplates = this.$filter('orderBy')(this.templateSelectorSvc.getAllTemplates(), ['projectType', 'displayName']);
      this.filterAndSortTemplates();
    });

    this.projectSourceSelectorService.subscribe(this.onProjectTemplateAdded.bind(this));
  }

  /**
   * Callback which is called when stack is selected.
   */
  onStackChanged(): void {
    const stackId = this.stackSelectorSvc.getStackId();
    if (!stackId) {
      return;
    }

    const stack = this.stackSelectorSvc.getStackById(stackId);
    this.stackTags = stack ? stack.tags : [];

    this.filterAndSortTemplates();
    this.updateNumberOfSelectedTemplates();
  }

  /**
   * Callback which is called when project template is added to the list of ready-to-import projects.
   */
  onProjectTemplateAdded(projectTemplateName: string): void {
    this.cheListHelper.itemsSelectionStatus[projectTemplateName] = false;

    this.selectedTemplates = this.cheListHelper.getSelectedItems() as Array<che.IProjectTemplate>;
    this.updateNumberOfSelectedTemplates();
  }

  /**
   * Filters templates by tags and sort them by project type and template name.
   */
  filterAndSortTemplates(): void {
    const stackTags = !this.stackTags ? [] : this.stackTags.map((tag: string) => tag.toLowerCase());

    if (stackTags.length) {
      this.filteredTemplates = this.allTemplates.filter((template: che.IProjectTemplate) => {
        const templateTags = template.tags.map((tag: string) => tag.toLowerCase());
        return stackTags.some((tag: string) => templateTags.indexOf(tag) > -1);
      });
    }

    this.cheListHelper.setList(this.filteredTemplates, 'name');
    this.selectedTemplates.forEach((template: che.IProjectTemplate) => {
      this.cheListHelper.itemsSelectionStatus[template.name] = true;
    });
  }

  /**
   * Callback which is when the template checkbox is clicked.
   *
   * @param {string} templateName the project template's name
   * @param {boolean} isChecked <code>true</code> if template's checkbox is checked.
   */
  onTemplateClicked(templateName: string, isChecked: boolean): void {
    this.selectedTemplates = this.cheListHelper.getSelectedItems() as Array<che.IProjectTemplate>;
    this.templateSelectorSvc.onTemplatesSelected(this.selectedTemplates);

    this.updateNumberOfSelectedTemplates();
  }

  /**
   * Returns <code>true</code> if current template is not already added to the list of ready-to-import projects.
   *
   * @return {boolean}
   */
  isTemplateSelected(newTemplatesNumber: number): boolean {
    return newTemplatesNumber > 0;
  }

  /**
   * Update number of project's templates which will be added to ready-to-import projects.
   */
  updateNumberOfSelectedTemplates(): void {
    this.newTemplatesNumber = this.cheListHelper.getSelectedItems().length;
  }

}