"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { 
  Globe, MapPin, ShieldAlert, Sparkles, ArrowRight, CheckCircle2, 
  Search, LogIn, LogOut, ChevronRight, Activity, Users, Newspaper, Building2
} from 'lucide-react';
import { popularRegions } from "@/data/regions";

export default function Home() {
  const [region, setRegion] = useState("");
  const [user, setUser] = useState<{ email: string; subscription_type: string } | null>(null);
  const [emailInput, setEmailInput] = useState("");
  const [showSuggestions, setShowSuggestions] = useState(false);
  const router = useRouter();

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

  const handleLogin = async () => {
    if (!emailInput) return;
    try {
      const res = await fetch("http://localhost:8000/api/auth/login", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email: emailInput })
      });
      const data = await res.json();
      if (data.user) {
        setUser(data.user);
        localStorage.setItem("user", JSON.stringify(data.user));
      }
    } catch (err) {
      console.error("Login failed:", err);
    }
  };

  const handleLogout = () => {
    setUser(null);
    localStorage.removeItem("user");
  };

  const handleUpgrade = async () => {
    if (!user) return;
    try {
      const res = await fetch(`http://localhost:8000/api/user/upgrade?email=${user.email}`, {
        method: "POST"
      });
      const data = await res.json();
      if (data.status === "success") {
        const updatedUser = { ...user, subscription_type: "premium" };
        setUser(updatedUser);
        localStorage.setItem("user", JSON.stringify(updatedUser));
      }
    } catch (err) {
      console.error("Upgrade failed:", err);
    }
  };

  const filteredRegions = popularRegions
    .filter(r => 
      r.toLowerCase().startsWith(region.toLowerCase()) && 
      r.toLowerCase() !== region.toLowerCase()
    )
    .slice(0, 5);

  const openDashboard = (targetRegion?: string) => {
    const finalRegion = targetRegion || region || "Global";
    router.push(`/dashboard?region=${encodeURIComponent(finalRegion)}`);
  };

  return (
    <div className="min-h-screen bg-[#050505] text-white selection:bg-red-500/30">
      {/* Navigation */}
      <nav className="fixed top-0 w-full z-50 border-b border-white/5 bg-black/50 backdrop-blur-xl">
        <div className="max-w-7xl mx-auto px-6 h-16 flex items-center justify-between">
          <div className="flex items-center gap-2">
            <ShieldAlert className="w-6 h-6 text-red-500" />
            <span className="text-xl font-black tracking-tighter">Expose<span className="text-red-500">++</span></span>
          </div>
          
          <div className="flex items-center gap-4">
            {user ? (
              <div className="flex items-center gap-3">
                <div className="hidden sm:flex flex-col items-end">
                  <span className="text-xs text-gray-400">{user.email}</span>
                  <span className="text-[10px] font-bold text-orange-500 uppercase tracking-widest">{user.subscription_type}</span>
                </div>
                <button onClick={handleLogout} className="p-2 hover:bg-white/5 rounded-full transition-colors">
                  <LogOut className="w-5 h-5 text-gray-400" />
                </button>
              </div>
            ) : (
              <div className="flex items-center gap-2 bg-white/5 p-1 rounded-full border border-white/10">
                <input 
                  type="email" 
                  placeholder="Email" 
                  className="bg-transparent border-none outline-none px-3 py-1 text-sm w-32 sm:w-48"
                  value={emailInput}
                  onChange={(e) => setEmailInput(e.target.value)}
                />
                <button onClick={handleLogin} className="bg-white text-black px-4 py-1 rounded-full text-xs font-bold hover:bg-gray-200 transition-colors">
                  Login
                </button>
              </div>
            )}
          </div>
        </div>
      </nav>

      {/* Hero Section */}
      <main className="pt-32 pb-20 px-6 relative">
        <div className="absolute top-0 left-1/2 -translate-x-1/2 w-full max-w-4xl h-[500px] bg-red-500/10 blur-[120px] -z-10 rounded-full"></div>
        
        <div className="max-w-4xl mx-auto text-center">
          <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-red-500/10 border border-red-500/20 text-red-400 text-xs font-bold mb-8 animate-fade-in">
            <Activity className="w-3 h-3" />
            Live Civic Monitoring Active
          </div>
          
          <h1 className="text-5xl md:text-7xl font-black tracking-tighter mb-6 bg-gradient-to-b from-white to-gray-400 bg-clip-text text-transparent leading-[1.1]">
            Expose local issues before they become crises.
          </h1>
          
          <p className="text-lg md:text-xl text-gray-400 mb-12 max-w-2xl mx-auto leading-relaxed">
            AI-powered observatory that tracks public grievances, infrastructure gaps, and local news across India in real-time.
          </p>

          {/* Search Bar */}
          <div className="relative max-w-xl mx-auto group">
            <div className="relative flex items-center bg-white/5 border border-white/10 rounded-2xl p-2 focus-within:border-red-500/50 focus-within:ring-4 focus-within:ring-red-500/10 transition-all duration-300 backdrop-blur-sm">
              <Search className="w-5 h-5 text-gray-500 ml-3" />
              <input 
                type="text" 
                placeholder="Search your district (e.g. Pune, Nagpur...)"
                className="bg-transparent border-none outline-none flex-1 px-4 py-3 text-lg placeholder:text-gray-600"
                value={region}
                onChange={(e) => {
                  setRegion(e.target.value);
                  setShowSuggestions(true);
                }}
                onFocus={() => setShowSuggestions(true)}
              />
              <button 
                onClick={() => openDashboard()}
                className="bg-red-500 hover:bg-red-600 text-white p-3 rounded-xl transition-all active:scale-95"
              >
                <ArrowRight className="w-5 h-5" />
              </button>
            </div>

            {/* Suggestions */}
            {showSuggestions && region && filteredRegions.length > 0 && (
              <div className="absolute top-full left-0 right-0 mt-2 bg-[#0a0a0a] border border-white/10 rounded-2xl overflow-hidden z-20 shadow-2xl">
                {filteredRegions.map((loc) => (
                  <button
                    key={loc}
                    onClick={() => {
                      setRegion(loc);
                      setShowSuggestions(false);
                      openDashboard(loc);
                    }}
                    className="w-full text-left px-6 py-4 hover:bg-white/5 border-b border-white/5 last:border-0 flex items-center justify-between group"
                  >
                    <span className="text-gray-300 group-hover:text-white transition-colors">{loc}</span>
                    <ChevronRight className="w-4 h-4 text-gray-600 group-hover:text-red-500 transition-colors" />
                  </button>
                ))}
              </div>
            )}
          </div>

          <div className="mt-8 flex flex-wrap justify-center gap-4 text-xs text-gray-500">
            <span className="flex items-center gap-1"><CheckCircle2 className="w-3 h-3 text-red-500" /> Real-time Scraping</span>
            <span className="flex items-center gap-1"><CheckCircle2 className="w-3 h-3 text-red-500" /> AI Risk Scoring</span>
            <span className="flex items-center gap-1"><CheckCircle2 className="w-3 h-3 text-red-500" /> Verification Engine</span>
          </div>
        </div>
      </main>

      {/* Features Grid */}
      <section className="max-w-7xl mx-auto px-6 py-20 border-t border-white/5">
        <div className="grid grid-cols-1 md:grid-cols-3 gap-8">
          <div className="p-8 rounded-3xl bg-white/5 border border-white/10 hover:border-red-500/30 transition-colors group">
            <div className="w-12 h-12 rounded-2xl bg-red-500/10 flex items-center justify-center mb-6 group-hover:scale-110 transition-transform">
              <Users className="w-6 h-6 text-red-500" />
            </div>
            <h3 className="text-xl font-bold mb-3">For Citizens</h3>
            <p className="text-gray-400 text-sm leading-relaxed">Stay informed about local water cuts, road closures, and safety alerts in your specific neighborhood.</p>
          </div>
          
          <div className="p-8 rounded-3xl bg-white/5 border border-white/10 hover:border-red-500/30 transition-colors group">
            <div className="w-12 h-12 rounded-2xl bg-red-500/10 flex items-center justify-center mb-6 group-hover:scale-110 transition-transform">
              <Newspaper className="w-6 h-6 text-red-500" />
            </div>
            <h3 className="text-xl font-bold mb-3">For Journalists</h3>
            <p className="text-gray-400 text-sm leading-relaxed">Track emerging trends and data-backed civic failures for investigative reporting and public accountability.</p>
          </div>

          <div className="p-8 rounded-3xl bg-white/5 border border-white/10 hover:border-red-500/30 transition-colors group">
            <div className="w-12 h-12 rounded-2xl bg-red-500/10 flex items-center justify-center mb-6 group-hover:scale-110 transition-transform">
              <Building2 className="w-6 h-6 text-red-500" />
            </div>
            <h3 className="text-xl font-bold mb-3">For Governance</h3>
            <p className="text-gray-400 text-sm leading-relaxed">Identify high-priority issues early and monitor the pulse of your district's most critical public needs.</p>
          </div>
        </div>
      </section>

      {/* Pricing */}
      <section className="max-w-4xl mx-auto px-6 py-20">
        <div className="text-center mb-12">
          <h2 className="text-3xl font-bold mb-4 italic">The Intelligence Edge</h2>
          <p className="text-gray-500">Free discovery for everyone, premium tools for power users.</p>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-8">
          <div className="bg-[#0a0a0a] border border-white/10 p-8 rounded-3xl">
            <h4 className="text-lg font-bold mb-2">Free Plan</h4>
            <div className="text-4xl font-black mb-6">$0</div>
            <ul className="space-y-4 text-sm text-gray-400 mb-8">
              <li className="flex items-center gap-2"><CheckCircle2 className="w-4 h-4 text-gray-600" /> Global issue browsing</li>
              <li className="flex items-center gap-2"><CheckCircle2 className="w-4 h-4 text-gray-600" /> Basic AI summaries</li>
              <li className="flex items-center gap-2"><CheckCircle2 className="w-4 h-4 text-gray-600" /> Community upvoting</li>
            </ul>
            <button className="w-full py-3 rounded-xl border border-white/10 font-bold opacity-50 cursor-default">Current</button>
          </div>

          <div className="bg-gradient-to-br from-red-500/10 to-transparent border border-red-500/20 p-8 rounded-3xl relative">
            <div className="absolute top-4 right-4 bg-red-500 text-[10px] font-black px-2 py-1 rounded-full uppercase tracking-widest">Premium</div>
            <h4 className="text-lg font-bold mb-2">Premium Plan</h4>
            <div className="text-4xl font-black mb-6">$19<span className="text-sm font-normal text-gray-500">/mo</span></div>
            <ul className="space-y-4 text-sm text-gray-400 mb-8">
              <li className="flex items-center gap-2"><CheckCircle2 className="w-4 h-4 text-red-500" /> <strong>Real-time district scraping</strong></li>
              <li className="flex items-center gap-2"><CheckCircle2 className="w-4 h-4 text-red-500" /> Instant spike alerts</li>
              <li className="flex items-center gap-2"><CheckCircle2 className="w-4 h-4 text-red-500" /> Data exports (CSV/PDF)</li>
            </ul>
            {user?.subscription_type === "premium" ? (
              <button className="w-full py-3 rounded-xl bg-red-500/20 text-red-400 font-bold cursor-default">Active</button>
            ) : (
              <button onClick={handleUpgrade} className="w-full py-3 rounded-xl bg-red-500 hover:bg-red-600 font-bold transition-all shadow-[0_0_20px_rgba(239,68,68,0.2)]">Upgrade Now</button>
            )}
          </div>
        </div>
      </section>

      {/* Footer */}
      <footer className="border-t border-white/5 py-12 px-6">
        <div className="max-w-7xl mx-auto flex flex-col md:flex-row justify-between items-center gap-6">
          <div className="flex items-center gap-2 opacity-50">
            <ShieldAlert className="w-5 h-5" />
            <span className="font-bold">Expose++</span>
          </div>
          <p className="text-xs text-gray-600">© 2026 Civic Intelligence Platform. Powered by Groq AI.</p>
        </div>
      </footer>
    </div>
  );
}
