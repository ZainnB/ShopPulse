# ShopPulse: 30-second demo script

Start the app, open `http://localhost:8080/login`.

1. **0-5s: log in.** Click **Fill demo merchant**. It signs in as `merchant@shoppulse.dev` / `merchant123` and lands on `/merchant/dashboard`. (Customer: `customer@shoppulse.dev` / `customer123`.)
2. **5-10s: KPIs.** Point at the four cards (revenue, orders, average order value, repeat-customer rate). Each shows the change against the previous period of equal length: green up arrow or red down arrow. Change the date range to show everything re-fetch.
3. **10-18s: ask a question.** In **Ask your store**, click the example chip or type exactly:
   - `Which category dropped the most this month?`
   - then, if time allows: `What were my top 5 products last week?`

   Open **Generated SQL** to show the query (read-only analytics views only), then point at the result table and the chart.
4. **18-24s: click an anomaly.** On the revenue line chart, click one of the large points (or its chip below the chart). The side panel shows the explanation and its supporting facts.
5. **24-30s: briefing.** Scroll to **Weekly AI briefing**. If it says none exists yet, click **Generate one now**; otherwise click **Regenerate**.

If you see the badge "rule-based fallback - no model key configured", the app is running without an Anthropic key. Answers come from deterministic rules instead of the model. Set the API key to see model-generated output.

Other example questions: `How did revenue trend over the last 90 days?` and `Which customers spent the most?`
