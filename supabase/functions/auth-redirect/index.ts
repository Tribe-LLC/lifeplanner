// Edge function for email verification callbacks.
// Shows confirmation or error after Supabase processes the email link.

Deno.serve((req: Request) => {
  const supabaseUrl = Deno.env.get('SUPABASE_URL') || '';

  const html = `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Life Planner</title>
  <style>
    * { margin: 0; padding: 0; box-sizing: border-box; }
    body {
      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
      background: linear-gradient(135deg, #0f0f1a 0%, #1a1a2e 50%, #16213e 100%);
      color: white;
      min-height: 100vh;
      display: flex;
      align-items: center;
      justify-content: center;
      text-align: center;
      padding: 24px;
    }
    .container { max-width: 400px; width: 100%; }
    .icon { font-size: 64px; margin-bottom: 20px; }
    h1 { font-size: 24px; margin-bottom: 12px; }
    h1.success {
      background: linear-gradient(90deg, #00f0ff, #4CAF50);
      -webkit-background-clip: text;
      -webkit-text-fill-color: transparent;
    }
    h1.error {
      background: linear-gradient(90deg, #ff6b6b, #ff8e53);
      -webkit-background-clip: text;
      -webkit-text-fill-color: transparent;
    }
    p { font-size: 15px; opacity: 0.7; margin-bottom: 20px; line-height: 1.6; }
    .highlight {
      display: inline-block;
      background: rgba(0, 240, 255, 0.1);
      border: 1px solid rgba(0, 240, 255, 0.3);
      border-radius: 8px;
      padding: 12px 20px;
      margin: 8px 0 20px;
      font-size: 14px;
      opacity: 0.9;
    }
    .hidden { display: none; }
    hr {
      margin: 28px 0;
      border: none;
      border-top: 1px solid rgba(255,255,255,0.1);
    }
    input[type="email"] {
      width: 100%;
      padding: 14px 16px;
      border-radius: 12px;
      border: 1px solid rgba(255,255,255,0.2);
      background: rgba(255,255,255,0.08);
      color: white;
      font-size: 16px;
      margin-bottom: 12px;
      outline: none;
    }
    input[type="email"]::placeholder { color: rgba(255,255,255,0.3); }
    input[type="email"]:focus { border-color: #00f0ff; }
    button.btn-resend {
      width: 100%;
      padding: 14px;
      background: rgba(255,255,255,0.12);
      color: white;
      border: 1px solid rgba(255,255,255,0.2);
      border-radius: 12px;
      font-size: 16px;
      font-weight: 600;
      cursor: pointer;
      transition: background 0.2s;
    }
    button.btn-resend:hover { background: rgba(255,255,255,0.18); }
    button.btn-resend:disabled { opacity: 0.5; cursor: not-allowed; }
    .resend-status {
      margin-top: 12px;
      font-size: 14px;
      min-height: 20px;
    }
    .resend-status.ok { color: #4CAF50; opacity: 1; }
    .resend-status.fail { color: #ff6b6b; opacity: 1; }
  </style>
</head>
<body>
  <div class="container">
    <!-- Success state -->
    <div id="success" class="hidden">
      <div class="icon">&#x2705;</div>
      <h1 class="success">Email Verified!</h1>
      <p>Your account has been confirmed.</p>
      <div class="highlight">Go back to Life Planner and sign in with your email and password.</div>
      <p style="font-size:13px; opacity:0.4;">You can close this tab now.</p>
    </div>
    <!-- Error state -->
    <div id="error" class="hidden">
      <div class="icon">&#x26A0;&#xFE0F;</div>
      <h1 class="error" id="errorTitle">Link Expired</h1>
      <p id="errorMsg">This verification link has expired.</p>
      <hr>
      <p style="opacity:0.5; font-size:14px;">Enter your email to receive a new verification link:</p>
      <input type="email" id="resendEmail" placeholder="your@email.com" />
      <button class="btn-resend" id="resendBtn" onclick="resendEmail()">Resend Verification Email</button>
      <p class="resend-status" id="resendStatus"></p>
    </div>
  </div>
  <script>
    var RESEND_URL = '${supabaseUrl}/functions/v1/resend-verification';

    var hash = window.location.hash.substring(1);
    var params = new URLSearchParams(hash);
    var error = params.get('error');
    var errorDesc = params.get('error_description');
    var errorCode = params.get('error_code');

    if (error) {
      document.getElementById('error').classList.remove('hidden');
      if (errorCode === 'otp_expired') {
        document.getElementById('errorTitle').textContent = 'Link Expired';
        document.getElementById('errorMsg').textContent =
          'This verification link has expired. Request a new one below.';
      } else {
        document.getElementById('errorTitle').textContent = 'Verification Failed';
        document.getElementById('errorMsg').textContent =
          errorDesc ? decodeURIComponent(errorDesc.replace(/\\+/g, ' ')) : 'Something went wrong. Please try again.';
      }
    } else {
      document.getElementById('success').classList.remove('hidden');
    }

    function resendEmail() {
      var email = document.getElementById('resendEmail').value.trim();
      var status = document.getElementById('resendStatus');
      var btn = document.getElementById('resendBtn');

      if (!email) {
        status.textContent = 'Please enter your email address.';
        status.className = 'resend-status fail';
        return;
      }

      btn.disabled = true;
      btn.textContent = 'Sending...';
      status.textContent = '';
      status.className = 'resend-status';

      fetch(RESEND_URL, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email: email }),
      })
      .then(function(res) { return res.json(); })
      .then(function(data) {
        if (data.success) {
          status.textContent = 'Verification email sent! Check your inbox.';
          status.className = 'resend-status ok';
        } else {
          status.textContent = data.error || 'Failed to send. Please try again.';
          status.className = 'resend-status fail';
        }
      })
      .catch(function() {
        status.textContent = 'Network error. Please try again.';
        status.className = 'resend-status fail';
      })
      .finally(function() {
        btn.disabled = false;
        btn.textContent = 'Resend Verification Email';
      });
    }
  </script>
</body>
</html>`;

  return new Response(html, {
    headers: { "Content-Type": "text/html; charset=utf-8" },
  });
});
