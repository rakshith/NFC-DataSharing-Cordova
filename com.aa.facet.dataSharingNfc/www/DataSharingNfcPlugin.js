/**
 * Created by sraksh on 9/1/2016.
 */

var exec = require('cordova/exec'),
      cordova = require('cordova');


/**
 * @constructor
 */
function DataSharingNfcPlugin() {
    // To keep track if data received call back is registered or not.
    // This listener should be registered only once.
    this.dataReceiveCallBack = null;
}

DataSharingNfcPlugin.prototype.showNfcSettings = function(action, successCallback, errorCallback) {
    exec(successCallback, errorCallback, "DataSharingNfcPlugin", "showNfcSettings",[]);
};

DataSharingNfcPlugin.prototype.shareData = function(data, successCallback, errorCallback) {
    exec(successCallback, errorCallback, "DataSharingNfcPlugin", "shareData",[data]);
};

DataSharingNfcPlugin.prototype.stopShareData = function(successCallback, errorCallback) {
    exec(successCallback, errorCallback, "DataSharingNfcPlugin", "stopShareData",[]);
};

DataSharingNfcPlugin.prototype.getNfcStatus = function(successCallback, errorCallback) {
    exec(successCallback, errorCallback, "DataSharingNfcPlugin", "getNfcStatus",[]);
};

DataSharingNfcPlugin.prototype.receiveData = function(dataReceiveCallBack, listenerRegisteredCallBack, listenerRegisteredErrorCallBack) {
    if (!dataSharingNfcPlugin.dataReceiveCallBack) {
        dataSharingNfcPlugin.dataReceiveCallBack = dataReceiveCallBack;
        document.addEventListener("readNdefDataListener", dataReceiveCallBack, false);
    } else {
        console.log( " Already registered " );
    }
    exec(listenerRegisteredCallBack, listenerRegisteredErrorCallBack, "DataSharingNfcPlugin", "receiveData",[]);
};

var dataSharingNfcPlugin = new DataSharingNfcPlugin();

module.exports = dataSharingNfcPlugin;