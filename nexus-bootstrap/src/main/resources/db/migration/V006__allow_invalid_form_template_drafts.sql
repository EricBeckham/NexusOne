-- K1 follow-up: draft authoring must retain validation errors for K4 to report.
ALTER TABLE platform.form_template_field
    DROP CONSTRAINT form_template_field_key_unique,
    DROP CONSTRAINT form_template_select_options;
