const {contextBridge,clipboard,ipcRenderer}=require('electron');
contextBridge.exposeInMainWorld('neo',{
  copy:(text)=>clipboard.writeText(String(text)),
  open:(url)=>ipcRenderer.invoke('neo:openExternal',String(url))
});