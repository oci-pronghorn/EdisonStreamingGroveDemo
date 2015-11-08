'use strict';
/* global angular */

console.log('app.js x: creating myApp module');
const app = angular.module('myApp', [
  'myApp.directives', 'myApp.filters', 'myApp.services',
  'ngRoute', 'ngSanitize', 'ngTouch'
]);
console.log('app.js x: created myApp module');


app.config(['$routeProvider', $routeProvider => {
  $routeProvider.when('/view1', {
    templateUrl: 'partials/partial1.html',
    controller: 'MyCtrl',
    view: 'center'
  });
  $routeProvider.otherwise({redirectTo: '/view1'});
}]);


app.controller('MyCtrl', (function($scope) {
	
	  if (!WebSocket) window.WebSocket = window.MozWebSocket;
	  if (!WebSocket) {
		    return alert('Your browser does not support WebSockets.');
	  }

	  var socket = null;

	  function startupWebSocket() {
	  	   socket = new WebSocket("ws://"+location.host+"/websocket");
	      
	       socket.binaryType = 'arraybuffer';
	       socket.onmessage = function(event) {
				    	
		            var view = new Uint8Array(event.data);

   	                var dataView = new Uint32Array(event.data);	                	                      
	                var t = (dataView[1]*(65536*65536))+dataView[2]; 
	                //NOTE: we use local time or chart may be off screen.    
	                line[view[0]-1].append(new Date().getTime(), dataView[3]);	         

	       }
	       
	      socket.onopen = function(event) {
				   console.log('Web Socket opened!');
		  }
		  
		  socket.onclose = function(event) {
				    console.log('Web Socket closed');
				    checkWebSocketStatus();
				    
		  }

	  }
	  
	  function checkWebSocketStatus(){
   			 if(!socket || socket.readyState == 3) startupWebSocket();
  	  }
  	  
	  startupWebSocket();
	  setInterval(checkWebSocketStatus, 3000);
	  
      //need one of these per canvas
      const smoothie = [new SmoothieChart({millisPerPixel:40,interpolation:'linear'}),
				        new SmoothieChart({millisPerPixel:40,interpolation:'linear'}),
				        new SmoothieChart({millisPerPixel:40,interpolation:'linear'}),
				        new SmoothieChart({millisPerPixel:40,maxValue:100000000,minValue:0,labels:{disabled:true}})]
				   			            
	 smoothie[0].streamTo(document.getElementById("canvas"+1));		  
	 smoothie[1].streamTo(document.getElementById("canvas"+2));	
	 smoothie[2].streamTo(document.getElementById("canvas"+3));	
	 smoothie[3].streamTo(document.getElementById("canvas"+4),250);	 
	
      const line = [new TimeSeries(), //0sine
	                new TimeSeries(), //1random
	                new TimeSeries(), //2system cpu
	                new TimeSeries(), //3process cpu
	                new TimeSeries(), //4motion
	                new TimeSeries(), //5light
	                new TimeSeries(), //6uv
	                new TimeSeries(), //7mosit
	                new TimeSeries(), //8button 
	                new TimeSeries()  //9rotery
	                ]
                								
			
	  smoothie[0].addTimeSeries(line[1]);
	  smoothie[0].addTimeSeries(line[0],{ strokeStyle:'rgb(0, 255, 0)', fillStyle:'rgba(0, 255, 0, 0.4)', lineWidth:2 });

      smoothie[1].addTimeSeries(line[5],{ strokeStyle:'rgb(255, 255, 255)', fillStyle:'rgba(255, 255, 255, 0.4)', lineWidth:2 }); //light
      smoothie[1].addTimeSeries(line[6],{ strokeStyle:'rgb(255, 0, 255)', fillStyle:'rgba(255, 0, 255, 0.4)', lineWidth:2 }); //uv
      smoothie[1].addTimeSeries(line[7],{ strokeStyle:'rgb(165,42,42)', fillStyle:'rgba(165,42,42, 0.4)', lineWidth:2 }); //mositure
      
      
      smoothie[2].addTimeSeries(line[4],{ strokeStyle:'rgb(255, 255, 255)', fillStyle:'rgba(255, 255, 255, 0.4)', lineWidth:2 }); //motion -- moving average.
      smoothie[2].addTimeSeries(line[8],{ strokeStyle:'rgb(255, 0, 0)', fillStyle:'rgba(255, 0, 0, 0.4)', lineWidth:2 }); //button -- spike
      smoothie[2].addTimeSeries(line[9],{ strokeStyle:'rgb(0, 0, 255)', fillStyle:'rgba(0, 0, 255, 0.4)', lineWidth:2 }); //rotary --must bound turn up and down
      

	  smoothie[3].addTimeSeries(line[3],{ strokeStyle:'rgb(0, 255, 0)', fillStyle:'rgba(0, 255, 0, 0.4)', lineWidth:2 });
	  smoothie[3].addTimeSeries(line[2],{ strokeStyle:'rgb(255, 0, 0)', fillStyle:'rgba(255, 0, 0, 0.4)', lineWidth:2 });//on top last

		$scope.send = function(message) {
		   if (socket.readyState === WebSocket.OPEN) {
		      	socket.send(message);
		    } else {
		    	console.log('The socket is not open.');
		    }
		}		
		
		$scope.subscribe = function(subId) {
	 	   if (socket.readyState === WebSocket.OPEN) {
				var subMsg = new ArrayBuffer(2);			
				var byteView = new Uint8Array(subMsg);
				byteView[0] = subId;
				byteView[1] = 1;//subscribe			
			   	socket.send(subMsg);	
		    } else {
		    	console.log('The socket is not open.');
		    }
		}
		
		$scope.unsubscribe = function(subId) {
		   if (socket.readyState === WebSocket.OPEN) {
				var subMsg = new ArrayBuffer(2);			
				var byteView = new Uint8Array(subMsg);
				byteView[0] = subId;
				byteView[1] = 0;//unsubscribe	
			   	socket.send(subMsg);
		    } else {
		    	console.log('The socket is not open.');
		    }
		}      
 }));
