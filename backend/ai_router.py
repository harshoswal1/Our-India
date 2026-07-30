import os
import json
from groq import Groq
from google import genai
from google.genai import types
from dotenv import load_dotenv

load_dotenv()

GROQ_API_KEY = os.getenv("GROQ_API_KEY")
GEMINI_API_KEY = os.getenv("GEMINI_API_KEY")

# Initialize clients
groq_client = Groq(api_key=GROQ_API_KEY) if GROQ_API_KEY else None
gemini_client = genai.Client(api_key=GEMINI_API_KEY) if GEMINI_API_KEY else None

def classify_news_article(title: str, summary: str):
    """
    Analyzes news using Groq (Llama-3.3-70b) and extracts structured civic metrics.
    """
    if not groq_client:
        # Fallback to local default mockup if Groq is not configured
        print("Groq client not configured, returning mock data.")
        return {
            "core_topic": "Infrastructure",
            "problem_rate": 50,
            "ai_reasoning": "Mock fallback classification.",
            "confidence_score": 80,
            "government_body": "PWD",
            "specific_location": "Unknown",
            "ai_suggested_solution": "Inspect local area road quality."
        }
    
    prompt = f"""
    You are an AI analyst for a public grievance platform in India called 'Our India'.
    Analyze the following news article title and summary to extract structural civic issues.
    
    Article Title: {title}
    Article Summary: {summary}
    
    Provide the response STRICTLY as a valid JSON object with the following keys:
    - "core_topic": (string) 1-3 word category like "Water Scarcity", "Potholes", "Crime", "Pollution", "Health", "Traffic", "Infrastructure".
    - "problem_rate": (integer 0-100) Severity score. 100 being catastrophic/life-threatening, 0 being minor.
    - "ai_reasoning": (string) Explaining why you assigned this severity score.
    - "confidence_score": (integer 0-100) How factual this issue appears (100 = verified report, 0 = opinion).
    - "government_body": (string) Specific authority responsible (e.g. PWD, Municipal Corporation, Police Department, Water Supply Department, MSEDCL).
    - "specific_location": (string) Extracted city, street, or ward name. Default to "Unknown".
    - "ai_suggested_solution": (string) 1-sentence recommended action for the department.
    """
    
    try:
        completion = groq_client.chat.completions.create(
            messages=[
                {"role": "system", "content": "You are a database system that outputs ONLY raw JSON conforming to the requested schema."},
                {"role": "user", "content": prompt}
            ],
            model="llama-3.3-70b-versatile",
            response_format={"type": "json_object"}
        )
        raw = completion.choices[0].message.content
        return json.loads(raw)
    except Exception as e:
        print(f"Groq classification failed: {e}. Trying Gemini fallback...")
        return _classify_with_gemini_fallback(title, summary)

def _classify_with_gemini_fallback(title: str, summary: str):
    if not gemini_client:
        return {
            "core_topic": "General",
            "problem_rate": 30,
            "ai_reasoning": "Fallback to defaults due to total API failure.",
            "confidence_score": 50,
            "government_body": "Municipal Council",
            "specific_location": "Unknown",
            "ai_suggested_solution": "Investigate reported issues."
        }
    try:
        prompt = f"Analyze and return a JSON with core_topic, problem_rate (0-100), ai_reasoning, confidence_score, government_body, specific_location, ai_suggested_solution for news: {title} - {summary}"
        response = gemini_client.models.generate_content(
            model='gemini-2.5-flash',
            contents=prompt,
            config=types.GenerateContentConfig(
                response_mime_type="application/json",
            ),
        )
        return json.loads(response.text)
    except Exception as ex:
        print(f"Gemini fallback classification failed: {ex}")
        return {
            "core_topic": "General",
            "problem_rate": 30,
            "ai_reasoning": "Fallback default.",
            "confidence_score": 50,
            "government_body": "Municipal Council",
            "specific_location": "Unknown",
            "ai_suggested_solution": "Investigate reported issues."
        }

def verify_civic_image(image_bytes: bytes, mime_type: str = "image/jpeg"):
    """
    Uses Gemini 2.5 Flash to verify if an uploaded image represents a real public grievance issue.
    """
    if not gemini_client:
        print("Gemini API not configured, defaulting to verification success.")
        return {"valid": True, "confidence": 95, "comments": "Bypassed verification (No Key)."}
    
    prompt = """
    Analyze this image to verify if it represents a genuine public/civic grievance issue in India.
    Civic issues include: damaged roads/potholes, public garbage dumping, pipe leakage, dangling electrical wires, public infrastructure damage, traffic issues.
    
    Return a JSON object:
    {
      "valid": true/false,
      "confidence": (integer 0-100),
      "comments": "Brief description of what is seen in the image."
    }
    """
    try:
        response = gemini_client.models.generate_content(
            model='gemini-2.5-flash',
            contents=[
                types.Part.from_bytes(
                    data=image_bytes,
                    mime_type=mime_type,
                ),
                prompt
            ],
            config=types.GenerateContentConfig(
                response_mime_type="application/json",
            ),
        )
        return json.loads(response.text)
    except Exception as e:
        print(f"Gemini image verification failed: {e}")
        return {"valid": True, "confidence": 50, "comments": "Failed AI check; auto-approved."}

def generate_legal_answer(query: str, context: str):
    """
    Generates a legal advice RAG response using Groq or Gemini.
    """
    prompt = f"""
    You are an expert AI Legal Advisor for citizens in India on the platform 'Our India'.
    Answer the following legal query using ONLY the verified source context below.
    If the context doesn't contain sufficient details to answer, state clearly that official documentation was not found.
    Provide a professional, neutral, plain-language response with citations of relevant acts or rules if available.
    
    Legal Context Sources:
    {context}
    
    User Query: {query}
    """
    
    # Try Groq Llama first
    if groq_client:
        try:
            completion = groq_client.chat.completions.create(
                messages=[
                    {"role": "system", "content": "You are a professional legal assistant. Always cite relevant clauses and add a disclaimer."},
                    {"role": "user", "content": prompt}
                ],
                model="llama-3.3-70b-versatile"
            )
            return completion.choices[0].message.content
        except Exception as e:
            print(f"Groq legal RAG failed: {e}. Trying Gemini...")
            
    # Try Gemini as primary/fallback
    if gemini_client:
        try:
            response = gemini_client.models.generate_content(
                model='gemini-2.5-flash',
                contents=prompt
            )
            return response.text
        except Exception as e:
            print(f"Gemini legal RAG failed: {e}")
            
    return "Unable to process the legal query at this moment due to connection issues with our AI models. Please try again later."

def generate_embedding(text: str):
    """
    Generates 3072-dimensional text embeddings using Gemini's gemini-embedding-2.
    """
    if not gemini_client:
        # Fallback dummy embedding (e.g. list of floats)
        return [0.0] * 3072
    try:
        response = gemini_client.models.embed_content(
            model='gemini-embedding-2',
            contents=text
        )
        return response.embeddings[0].values
    except Exception as e:
        print(f"Embedding generation failed: {e}")
        return [0.0] * 3072
