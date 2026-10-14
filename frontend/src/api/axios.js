import axios from 'axios';

/**
 * Shared Axios client configured for proxying requests through Vite.
 * Uses a relative baseURL of '/api' so all requests are automatically proxied
 * to http://localhost:8080 during local development and seamlessly work
 * through any public ngrok tunnel.
 */
const api = axios.create({
  baseURL: '/api',
  headers: {
    'Content-Type': 'application/json',
  },
});

export default api;
export { api };
