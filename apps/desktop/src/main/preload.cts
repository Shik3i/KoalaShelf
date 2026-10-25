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
    saveFile: (options: { name: string; content: string; isBase64?: boolean; filters?: Array<{ name: string; extensions: string[] }> }) => Promise<boolean>;
  };
  media: {
    updateState: (state: { title: string; author: string; coverUrl?: string; playing: boolean; position: number; duration: number }) => void;
    onMediaCommand: (callback: (cmd: 'play-pause' | 'skip-back' | 'skip-forward' | 'stop') => void) => () => void;
  };
  netFetch: (
    url: string,
    init?: { method?: string; headers?: Record<string, string>; body?: string }
  ) => Promise<{
    ok: boolean;
    status: number;
    statusText: string;
    headers: Record<string, string>;
    body: Uint8Array | string;
  }>;
}

const bridge: ElectronBridge = {
  isElectron: true,
  platform: process.platform,
  netFetch: (url, init) => ipcRenderer.invoke('net:fetch', url, init),
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
    showSaveDialog: (options) => ipcRenderer.invoke('dialog:showSaveDialog', options),
    saveFile: (options) => ipcRenderer.invoke('dialog:saveFile', options)
  },
  media: {
    updateState: (state) => ipcRenderer.send('media:updateState', state),
    onMediaCommand: (callback) => {
      const handler = (_event: Electron.IpcRendererEvent, cmd: 'play-pause' | 'skip-back' | 'skip-forward' | 'stop') => callback(cmd);
      ipcRenderer.on('media:command', handler);
      return () => {
        ipcRenderer.removeListener('media:command', handler);
      };
    }
  }
};

contextBridge.exposeInMainWorld('electron', bridge);
