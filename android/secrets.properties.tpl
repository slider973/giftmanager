# Modèle de config Android — rendu par scripts/secrets/inject.sh vers app/secrets.properties (ignoré par git).
# Ne contient QUE des valeurs publiques côté client. Jamais la secret_key (service_role) Supabase.
SUPABASE_URL={{ op://giftmanager/supabase/url }}
SUPABASE_PUBLISHABLE_KEY={{ op://giftmanager/supabase/publishable_key }}
GOOGLE_WEB_CLIENT_ID={{ op://giftmanager/google-oauth/web_client_id }}
# Connexion Apple par le web (#83) : « true » une fois le Services ID créé et renseigné dans Supabase.
APPLE_SIGN_IN_ENABLED={{ op://giftmanager/apple-signin-web/enabled }}
# Firebase (notifications push, #76) : valeurs publiques du projet, relevées dans la console Firebase.
FIREBASE_API_KEY={{ op://giftmanager/fcm/api_key }}
FIREBASE_APP_ID={{ op://giftmanager/fcm/app_id }}
FIREBASE_PROJECT_ID={{ op://giftmanager/fcm/project_id }}
FIREBASE_SENDER_ID={{ op://giftmanager/fcm/sender_id }}
