import { Client, type IFrame, type IMessage, type StompSubscription } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

const baseURL = import.meta.env.VITE_API_BASE_URL ?? '';

let client: Client | null = null;
let connectPromise: Promise<Client> | null = null;

function buildClient(): Client {
  const c = new Client({
    webSocketFactory: () => new SockJS(`${baseURL}/ws`),
    reconnectDelay: 2_000,
    heartbeatIncoming: 10_000,
    heartbeatOutgoing: 10_000,
    debug: () => {
      /* silent */
    },
    onStompError: (frame: IFrame) => {
      console.warn('STOMP error', frame.headers.message);
    },
  });
  return c;
}

function connect(): Promise<Client> {
  if (client?.connected) return Promise.resolve(client);
  if (connectPromise) return connectPromise;
  client = buildClient();
  connectPromise = new Promise<Client>((resolve, reject) => {
    if (!client) {
      reject(new Error('client not initialised'));
      return;
    }
    client.onConnect = () => resolve(client!);
    client.onWebSocketError = (e) => reject(e);
    client.activate();
  }).finally(() => {
    connectPromise = null;
  });
  return connectPromise;
}

export async function subscribeToMarket(
  conditionId: string,
  onMessage: (payload: unknown) => void,
): Promise<() => void> {
  const c = await connect();
  let subscription: StompSubscription | null = c.subscribe(`/topic/market/${conditionId}`, (m: IMessage) => {
    try {
      onMessage(JSON.parse(m.body));
    } catch {
      onMessage(m.body);
    }
  });
  return () => {
    subscription?.unsubscribe();
    subscription = null;
  };
}
