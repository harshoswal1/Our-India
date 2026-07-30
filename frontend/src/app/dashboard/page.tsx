"use client";

import { useEffect, useState, Suspense } from "react";
import { useSearchParams, useRouter } from "next/navigation";
import { 
  BarChart, Bar, XAxis, YAxis, Tooltip, ResponsiveContainer, CartesianGrid, Cell
} from 'recharts';
import { 
  Globe, MapPin, Activity, Flame, ShieldAlert, BadgeCheck, Map, ArrowRight,
  Filter, Clock, Share2, ThumbsUp, ExternalLink, ChevronDown,
  Sparkles, Bell, FileText, CheckCircle2, Home, Search
} from 'lucide-react';
import { Issue } from "@/types/issue";

function DashboardContent() {
  const searchParams = useSearchParams();
  const router = useRouter();
  const initialRegion = searchParams.get("region") || "Global";
  const [region, setRegion] = useState(initialRegion);
  const [issues, setIssues] = useState<Issue[]>([]);
  const [loading, setLoading] = useState(true);
  const [user, setUser] = useState<{ email: string; subscription_type: string } | null>(null);
  
  // Filters
  const [sortBy, setSortBy] = useState("severity_desc");
  const [timeRange, setTimeRange] = useState("all");
  const [topicFilter, setTopicFilter] = useState("");
  const [expandedIssue, setExpandedIssue] = useState<string | null>(null);

  const topics = ["All", "Water Scarcity", "Infrastructure", "Crime", "Pollution", "Health", "Traffic"];

  useEffect(() => {
    const savedUser = localStorage.getItem("user");
    if (savedUser && savedUser !== "undefined") {
      try {
        setUser(JSON.parse(savedUser));
      } catch (e) {
        console.error("Error parsing saved user:", e);
      }
    }
  }, []);

  useEffect(() => {
    const fetchIssues = async () => {
      setLoading(true);
      const query = new URLSearchParams({
        region: region || "global",
        time_range: timeRange,
        sort_by: sortBy,
        ...(topicFilter ? { topic: topicFilter } : {}),
        ...(user?.email ? { user_email: user.email } : {})
      });

      try {
        const res = await fetch(`http://localhost:8000/api/issues?${query.toString()}`);
        const data = await res.json();
        setIssues((data.issues || []) as Issue[]);
      } catch (err) {
        console.error(err);
      } finally {
        setLoading(false);
      }
    };

    fetchIssues();
  }, [region, sortBy, timeRange, topicFilter, user?.email]);

  const handleUpvote = (id: string, index: number) => {
    fetch(`http://localhost:8000/api/issues/${id}/upvote`, { method: "POST" })
      .then(res => res.json())
      .then(data => {
        if (data.status === "success") {
          const newIssues = [...issues];
          newIssues[index].upvotes = (newIssues[index].upvotes || 0) + 1;
          setIssues(newIssues);
        }
      });
  };

  const handleShare = (issue: Issue) => {
    const text = `Critical issue in ${issue.region}: ${issue.title} (Severity: ${issue.analysis?.problem_rate}%).\nSee more on Expose++`;
    window.open(`https://twitter.com/intent/tweet?text=${encodeURIComponent(text)}`, '_blank');
  };

  const chartData = issues.slice(0, 5).map((iss) => ({
    name: iss.analysis?.core_topic || "Issue",
    Severity: iss.analysis?.problem_rate || 0
  }));

  return (
    <div className="min-h-screen bg-[#050505] text-gray-100 font-sans selection:bg-red-500/30">
      <div className="max-w-7xl mx-auto px-6 py-6">
        {/* Header */}
        <header className="mb-8 flex flex-col md:flex-row items-start md:items-center justify-between gap-4">
          <div className="flex items-center gap-3">
            <button 
              onClick={() => router.push('/')}
              className="p-2.5 bg-white/5 hover:bg-white/10 rounded-xl border border-white/10 transition-colors"
            >
              <Home className="w-4 h-4 text-red-500" />
            </button>
            <div>
              <div className="flex items-center gap-2">
                <h1 className="text-2xl font-black tracking-tighter">
                  {region} <span className="text-red-500">Observatory</span>
                </h1>
                {user?.subscription_type === "premium" && (
                  <span className="bg-red-500 text-white text-[8px] font-black px-1.5 py-0.5 rounded-full uppercase tracking-widest">
                    PRO
                  </span>
                )}
              </div>
              <p className="text-[11px] text-gray-500 flex items-center gap-1.5">
                <Activity className="w-3 h-3 text-emerald-500" /> Live monitoring active
              </p>
            </div>
          </div>

          <div className="flex items-center gap-2">
            {user?.subscription_type === "premium" && (
              <button 
                onClick={() => alert("Exporting data...")}
                className="flex items-center gap-2 bg-white/5 hover:bg-white/10 border border-white/10 px-3 py-2 rounded-lg text-[10px] font-bold transition-all"
              >
                <FileText className="w-3 h-3" /> Export
              </button>
            )}
            <div className="flex items-center bg-white/5 border border-white/10 rounded-lg p-0.5">
              <select 
                value={sortBy} 
                onChange={(e) => setSortBy(e.target.value)}
                className="bg-transparent text-[10px] font-bold border-none outline-none text-gray-500 px-2 py-1.5 cursor-pointer"
              >
                <option value="severity_desc">Severity</option>
                <option value="upvotes_desc">Upvotes</option>
                <option value="date_desc">Latest</option>
              </select>
            </div>
          </div>
        </header>

        {/* Topic Filters */}
        <div className="flex gap-2 overflow-x-auto pb-6 mb-2 no-scrollbar">
          {topics.map((t) => (
            <button
              key={t}
              onClick={() => setTopicFilter(t === "All" ? "" : t)}
              className={`px-4 py-2 rounded-full text-xs font-bold whitespace-nowrap border transition-all ${
                (t === "All" && !topicFilter) || topicFilter === t
                  ? "bg-red-500 border-red-500 text-white"
                  : "bg-white/5 border-white/10 text-gray-400 hover:border-white/20"
              }`}
            >
              {t}
            </button>
          ))}
        </div>

        <div className="grid grid-cols-1 lg:grid-cols-12 gap-8 items-start">
          {/* Main Content */}
          <div className="lg:col-span-8">
            {loading ? (
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                {[1, 2, 3, 4].map(i => (
                  <div key={i} className="h-48 bg-white/5 rounded-3xl animate-pulse border border-white/5"></div>
                ))}
              </div>
            ) : issues.length > 0 ? (
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                {issues.map((issue, index) => (
                  <div 
                    key={issue._id}
                    onClick={() => setExpandedIssue(expandedIssue === issue._id ? null : (issue._id || null))}
                    className={`group bg-white/5 border rounded-3xl p-5 hover:border-red-500/30 transition-all duration-300 cursor-pointer flex flex-col ${
                      expandedIssue === issue._id ? "md:col-span-2 border-red-500/50 ring-1 ring-red-500/20" : "border-white/10"
                    }`}
                  >
                    <div className="flex items-start justify-between gap-3 mb-3">
                      <div className="flex-1 min-w-0">
                        <div className="flex items-center gap-2 mb-1.5">
                          <span className="text-[9px] font-black uppercase tracking-widest px-2 py-0.5 rounded bg-white/5 text-gray-500">
                            {issue.analysis?.core_topic}
                          </span>
                        </div>
                        <h3 className={`font-bold transition-colors leading-tight truncate ${expandedIssue === issue._id ? "text-lg text-red-400" : "text-sm group-hover:text-red-400"}`}>
                          {issue.title}
                        </h3>
                      </div>
                      <div className={`flex flex-col items-end ${expandedIssue === issue._id ? "text-xl" : "text-base"}`}>
                        <span className={`font-black ${
                          issue.analysis?.problem_rate > 70 ? 'text-red-500' : 
                          issue.analysis?.problem_rate > 40 ? 'text-orange-500' : 'text-blue-500'
                        }`}>
                          {issue.analysis?.problem_rate}%
                        </span>
                        <span className="text-[8px] text-gray-600 uppercase font-bold tracking-tighter">Severity</span>
                      </div>
                    </div>

                    <p className={`text-xs text-gray-400 leading-relaxed mb-4 ${expandedIssue === issue._id ? "" : "line-clamp-2"}`}>
                      {issue.summary}
                    </p>

                    {expandedIssue === issue._id && (
                      <div className="mt-2 space-y-4 animate-fade-in">
                        <div className="p-4 bg-red-500/5 rounded-2xl border border-red-500/10">
                          <p className="text-xs text-red-200 leading-relaxed">
                            <Sparkles className="w-3 h-3 inline mr-2 mb-0.5" />
                            <strong>AI Recommendation:</strong> {issue.analysis?.ai_suggested_solution}
                          </p>
                        </div>
                        
                        <div className="grid grid-cols-2 gap-3">
                          <div className="p-3 rounded-xl bg-white/5 border border-white/5">
                            <div className="text-[9px] uppercase font-bold text-gray-600 mb-1">Responsibility</div>
                            <div className="text-[10px] text-gray-300 font-semibold">{issue.analysis?.government_body}</div>
                          </div>
                          <div className="p-3 rounded-xl bg-white/5 border border-white/5">
                            <div className="text-[9px] uppercase font-bold text-gray-600 mb-1">Confidence</div>
                            <div className="text-[10px] text-gray-300 font-semibold">{issue.analysis?.confidence_score}% Accuracy</div>
                          </div>
                        </div>
                      </div>
                    )}

                    <div className="mt-auto flex items-center justify-between border-t border-white/5 pt-4">
                      <div className="flex items-center gap-3">
                        <button 
                          onClick={(e) => { e.stopPropagation(); handleUpvote(issue._id!, index); }}
                          className="flex items-center gap-1.5 text-[10px] font-bold text-gray-500 hover:text-white transition-colors"
                        >
                          <ThumbsUp className="w-3 h-3" /> {issue.upvotes || 0}
                        </button>
                        <button 
                          onClick={(e) => { e.stopPropagation(); handleShare(issue); }}
                          className="flex items-center gap-1.5 text-[10px] font-bold text-gray-500 hover:text-white transition-colors"
                        >
                          <Share2 className="w-3 h-3" /> Share
                        </button>
                      </div>
                      <a 
                        href={issue.link} 
                        target="_blank" 
                        onClick={(e) => e.stopPropagation()}
                        className="flex items-center gap-1 text-[10px] font-bold text-red-500 hover:text-red-400 transition-colors"
                      >
                        Source <ExternalLink className="w-2.5 h-2.5" />
                      </a>
                    </div>
                  </div>
                ))}
              </div>
            ) : (
              <div className="py-20 text-center border border-dashed border-white/10 rounded-3xl">
                <Search className="w-12 h-12 text-gray-700 mx-auto mb-4" />
                <h3 className="text-xl font-bold text-gray-500">No issues found</h3>
                <p className="text-sm text-gray-600 mt-2">Try a different region or broader time range.</p>
              </div>
            )}
          </div>

          {/* Sidebar */}
          <div className="lg:col-span-4 space-y-4 lg:sticky lg:top-8">
            {/* Analytics Mini */}
            <div className="bg-white/5 border border-white/10 rounded-3xl p-5">
              <h3 className="text-[10px] font-bold uppercase tracking-widest text-gray-500 mb-4 flex items-center gap-2">
                <Flame className="w-3 h-3 text-orange-500" /> Risk Overview
              </h3>
              <div className="h-32">
                {issues.length > 0 ? (
                  <ResponsiveContainer width="100%" height="100%" minWidth={0} minHeight={0}>
                    <BarChart data={chartData}>
                      <Bar dataKey="Severity" radius={[4, 4, 0, 0]}>
                        {chartData.map((entry, index) => (
                          <Cell key={`cell-${index}`} fill={entry.Severity > 70 ? '#ef4444' : '#333'} />
                        ))}
                      </Bar>
                      <XAxis dataKey="name" hide />
                      <Tooltip 
                        contentStyle={{ backgroundColor: '#000', border: '1px solid #333', borderRadius: '12px' }}
                        itemStyle={{ color: '#fff', fontSize: '12px' }}
                      />
                    </BarChart>
                  </ResponsiveContainer>
                ) : (
                  <div className="h-full flex items-center justify-center text-xs text-gray-600 italic">No data</div>
                )}
              </div>
            </div>

            {/* Premium Upsell / Alert */}
            <div className="bg-gradient-to-br from-red-500 to-orange-600 rounded-3xl p-5 text-white relative overflow-hidden group">
              <Sparkles className="absolute -right-2 -bottom-2 w-20 h-20 opacity-20 group-hover:scale-110 transition-transform" />
              <h3 className="text-sm font-bold mb-1">Get Spike Alerts</h3>
              <p className="text-[11px] text-white/80 leading-relaxed mb-4">
                Instant notifications for critical severity in {region}.
              </p>
              <button 
                onClick={() => router.push('/#pricing')}
                className="w-full py-2 bg-black text-white rounded-xl text-[10px] font-black hover:bg-black/80 transition-colors"
              >
                Go Premium
              </button>
            </div>

            {/* Gov Contact Info Placeholder */}
            <div className="bg-white/5 border border-white/10 rounded-3xl p-5">
              <h3 className="text-[10px] font-bold uppercase tracking-widest text-gray-500 mb-4">Local Authorities</h3>
              <div className="space-y-3">
                <div className="flex items-center gap-3">
                  <div className="w-8 h-8 rounded-lg bg-white/5 flex items-center justify-center text-[10px] font-bold text-gray-400">MC</div>
                  <div>
                    <div className="text-[10px] font-bold">{region} Municipal</div>
                    <div className="text-[9px] text-gray-600">Civic Services</div>
                  </div>
                </div>
                <div className="flex items-center gap-3">
                  <div className="w-8 h-8 rounded-lg bg-white/5 flex items-center justify-center text-[10px] font-bold text-gray-400">PH</div>
                  <div>
                    <div className="text-[10px] font-bold">Police HQ</div>
                    <div className="text-[9px] text-gray-600">Public Safety</div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

export default function Dashboard() {
  return (
    <Suspense fallback={
      <div className="min-h-screen bg-[#050505] flex items-center justify-center">
        <Activity className="w-8 h-8 text-red-500 animate-spin" />
      </div>
    }>
      <DashboardContent />
    </Suspense>
  );
}
