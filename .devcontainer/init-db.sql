-- Initial database setup for Quasar ZIO development
-- This script runs when the PostgreSQL container starts for the first time

-- Create extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- Create schemas
CREATE SCHEMA IF NOT EXISTS quasar;
CREATE SCHEMA IF NOT EXISTS audit;

-- Set default search path
ALTER DATABASE quasar SET search_path TO quasar, public;

-- Create sample tables for ZIO development

-- Users table
CREATE TABLE IF NOT EXISTS quasar.users (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    email VARCHAR(255) UNIQUE NOT NULL,
    username VARCHAR(100) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    first_name VARCHAR(100),
    last_name VARCHAR(100),
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Files table (for CAS system)
CREATE TABLE IF NOT EXISTS quasar.files (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    canonical_hash VARCHAR(128) UNIQUE NOT NULL,
    original_hash VARCHAR(128) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(100),
    content_encoding VARCHAR(50),
    file_size BIGINT NOT NULL,
    storage_path TEXT,
    metadata JSONB,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    created_by UUID REFERENCES quasar.users(id)
);

-- File chunks table (for chunked uploads)
CREATE TABLE IF NOT EXISTS quasar.file_chunks (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    file_id UUID REFERENCES quasar.files(id) ON DELETE CASCADE,
    chunk_index INTEGER NOT NULL,
    chunk_hash VARCHAR(128),
    chunk_size BIGINT NOT NULL,
    offset_position BIGINT NOT NULL,
    is_last_chunk BOOLEAN DEFAULT false,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    UNIQUE(file_id, chunk_index)
);

-- Upload sessions table
CREATE TABLE IF NOT EXISTS quasar.upload_sessions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    upload_token VARCHAR(255) UNIQUE NOT NULL,
    user_id UUID REFERENCES quasar.users(id),
    file_meta JSONB NOT NULL,
    chunk_size BIGINT,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    is_completed BOOLEAN DEFAULT false,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Audit log table
CREATE TABLE IF NOT EXISTS audit.activity_log (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID,
    action VARCHAR(100) NOT NULL,
    resource_type VARCHAR(50),
    resource_id VARCHAR(255),
    details JSONB,
    ip_address INET,
    user_agent TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Create indexes for better performance
CREATE INDEX IF NOT EXISTS idx_users_email ON quasar.users(email);
CREATE INDEX IF NOT EXISTS idx_users_username ON quasar.users(username);
CREATE INDEX IF NOT EXISTS idx_users_active ON quasar.users(is_active);

CREATE INDEX IF NOT EXISTS idx_files_canonical_hash ON quasar.files(canonical_hash);
CREATE INDEX IF NOT EXISTS idx_files_original_hash ON quasar.files(original_hash);
CREATE INDEX IF NOT EXISTS idx_files_created_by ON quasar.files(created_by);
CREATE INDEX IF NOT EXISTS idx_files_content_type ON quasar.files(content_type);

CREATE INDEX IF NOT EXISTS idx_file_chunks_file_id ON quasar.file_chunks(file_id);
CREATE INDEX IF NOT EXISTS idx_file_chunks_chunk_index ON quasar.file_chunks(chunk_index);

CREATE INDEX IF NOT EXISTS idx_upload_sessions_token ON quasar.upload_sessions(upload_token);
CREATE INDEX IF NOT EXISTS idx_upload_sessions_user_id ON quasar.upload_sessions(user_id);
CREATE INDEX IF NOT EXISTS idx_upload_sessions_expires_at ON quasar.upload_sessions(expires_at);

CREATE INDEX IF NOT EXISTS idx_activity_log_user_id ON audit.activity_log(user_id);
CREATE INDEX IF NOT EXISTS idx_activity_log_action ON audit.activity_log(action);
CREATE INDEX IF NOT EXISTS idx_activity_log_created_at ON audit.activity_log(created_at);

-- Create updated_at trigger function
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Apply updated_at trigger to tables that have updated_at column
CREATE TRIGGER update_users_updated_at BEFORE UPDATE ON quasar.users
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Insert sample data for development
INSERT INTO quasar.users (email, username, password_hash, first_name, last_name) VALUES
('admin@quasar.dev', 'admin', crypt('admin123', gen_salt('bf')), 'Admin', 'User'),
('developer@quasar.dev', 'developer', crypt('dev123', gen_salt('bf')), 'Developer', 'User'),
('test@quasar.dev', 'testuser', crypt('test123', gen_salt('bf')), 'Test', 'User')
ON CONFLICT (email) DO NOTHING;

-- Grant permissions
GRANT USAGE ON SCHEMA quasar TO quasar;
GRANT USAGE ON SCHEMA audit TO quasar;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA quasar TO quasar;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA audit TO quasar;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA quasar TO quasar;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA audit TO quasar;

-- Set default privileges for future tables
ALTER DEFAULT PRIVILEGES IN SCHEMA quasar GRANT ALL ON TABLES TO quasar;
ALTER DEFAULT PRIVILEGES IN SCHEMA audit GRANT ALL ON TABLES TO quasar;
ALTER DEFAULT PRIVILEGES IN SCHEMA quasar GRANT ALL ON SEQUENCES TO quasar;
ALTER DEFAULT PRIVILEGES IN SCHEMA audit GRANT ALL ON SEQUENCES TO quasar;

COMMIT; 