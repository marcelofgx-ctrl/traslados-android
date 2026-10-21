"use strict";
/**
 * Non-visual compatibility contract for the native booking engine.
 * Quick Trip V7 owns the single-screen journey presentation and relocates
 * the original booking elements. This adapter replaces the retired 4-step
 * premium presentation; it does NOT create new booking, GPS or pricing logic.
 *
 * app.js requires mount(), activate(), current(), open(), placePresence()
 * and syncLocation(). In V7 these visual operations are owned by
 * quicktrip-clean.js; no old page is created and no old DOM is moved.
 */
(function(root){
  root.TrasladosPremiumJourney={
    mount:function(){
      return Object.freeze({
        activate:function(){},
        open:function(){},
        current:function(){return "route";},
        placePresence:function(){},
        syncLocation:function(){}
      });
    }
  };
})(window);
