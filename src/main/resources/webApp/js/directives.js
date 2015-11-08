'use strict';
/* global angular */

angular.module('myApp.directives', []).
  directive('appVersion', ['version', version =>
    (scope, element) => element.text(version)
  ]);
