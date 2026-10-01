// Modèle de configuration iOS — rendu par scripts/secrets/inject.sh vers Secrets.xcconfig (ignoré par git).
// Ne contient QUE des valeurs publiques côté client. Jamais la secret_key (service_role) Supabase.
// Note : "//" démarre un commentaire dans un xcconfig, d'où le "https:/$()/".

SUPABASE_URL = https:/$()/{{ op://giftmanager/supabase/project_ref }}.supabase.co
SUPABASE_PUBLISHABLE_KEY = {{ op://giftmanager/supabase/publishable_key }}
DEVELOPMENT_TEAM = {{ op://giftmanager/apple-developer/team_id }}
PRODUCT_BUNDLE_IDENTIFIER = {{ op://giftmanager/apple-developer/bundle_id }}
