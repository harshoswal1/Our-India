export type ProductTrack = "citizen" | "journalist" | "government";

export type IssueAnalysis = {
  core_topic?: string;
  problem_rate?: number;
  ai_reasoning?: string;
  confidence_score?: number;
  government_body?: string;
  specific_location?: string;
  ai_suggested_solution?: string;
};

export type Issue = {
  _id: string;
  title: string;
  link: string;
  region?: string;
  scraped_at: string;
  upvotes?: number;
  analysis?: IssueAnalysis;
};
