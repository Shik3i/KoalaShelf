import { contextBridge, ipcRenderer } from 'electron';

export interface ElectronBridge {
  isElectron: true;
  platform: NodeJS.Platform;
  storage: {
    getSecret: (key: string) => Promise<string | null>;
    setSecret: (key: string, value: string) => Promise<void>;
    deleteSecret: (key: string) => Promise<void>;
  };
  power: {
    inhibitSleep: (reason?: string) => Promise<number>;
    releaseSleep: (id: number) => Promise<void>;
  };
  window: {
    minimize: () => void;
    maximize: () => void;
    close: () => void;
    setAlwaysOnTop: (always: boolean) => void;
    openMiniPlayer: () => void;
  };
  dialog: {
    showSaveDialog: (options: { title?: string; defaultPath?: string; filters?: Array<{ name: string; extensions: string[] }> }) => Promise<string | null>;
  };
  media: {
    updateState: (state: { title: string; author: string; coverUrl?: string; playing: boolean; position: number; duration: number }) => void;
  };
}

const bridge: ElectronBridge = {
  isElectron: true,
  platform: process.platform,
  storage: {
    getSecret: (key: string) => ipcRenderer.invoke('storage:getSecret', key),
    setSecret: (key: string, value: string) => ipcRenderer.invoke('storage:setSecret', key, value),
    deleteSecret: (key: string) => ipcRenderer.invoke('storage:deleteSecret', key)
  },
  power: {
    inhibitSleep: (reason?: string) => ipcRenderer.invoke('power:inhibitSleep', reason),
    releaseSleep: (id: number) => ipcRenderer.invoke('power:releaseSleep', id)
  },
  window: {
    minimize: () => ipcRenderer.send('window:minimize'),
    maximize: () => ipcRenderer.send('window:maximize'),
    close: () => ipcRenderer.send('window:close'),
    setAlwaysOnTop: (always: boolean) => ipcRenderer.send('window:setAlwaysOnTop', always),
    openMiniPlayer: () => ipcRenderer.send('window:openMiniPlayer')
  },
  dialog: {
    showSaveDialog: (options) => ipcRenderer.invoke('dialog:showSaveDialog', options)
  },
  media: {
    updateState: (state) => ipcRenderer.send('media:updateState', state)
  }
};

contextBridge.exposeInMainWorld('electron', bridge);
