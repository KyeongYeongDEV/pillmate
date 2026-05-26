export type MessageRole = 'user' | 'ai';

export interface ChatSource {
  organization: string;
  document: string;
}

export interface ChatMessage {
  id: string;
  role: MessageRole;
  content: string;
  timestamp?: string;
  sources?: ChatSource[];
  hasWarning?: boolean;
  warningText?: string;
}

export interface ChatRequest {
  message: string;
  patientId: number;
}

export interface ChatResponse {
  id: string;
  content: string;
  sources: ChatSource[];
  hasWarning: boolean;
  warningText?: string;
}
