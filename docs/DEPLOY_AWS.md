# Deploying OrderHub to AWS EC2

This runs the app and PostgreSQL together with Docker Compose on one EC2 instance.

## 1. Launch an instance

1. AWS Console → **EC2** → **Launch instance**
2. **Name:** `orderhub`
3. **AMI:** Amazon Linux 2023
4. **Instance type:** `t3.micro` or `t2.micro` (Free Tier eligible)
5. **Key pair:** create one and download the `.pem` file
6. **Network settings → Edit → Security group rules:**
   - SSH, port 22, source **My IP**
   - Custom TCP, port **8080**, source **Anywhere (0.0.0.0/0)**
7. **Storage:** 20 GB gp3
8. Launch, then copy the **Public IPv4 address**

## 2. Install Docker

```bash
chmod 400 orderhub-key.pem
ssh -i orderhub-key.pem ec2-user@<PUBLIC_IP>

sudo dnf update -y
sudo dnf install -y docker git
sudo systemctl enable --now docker
sudo usermod -aG docker ec2-user

# Docker Compose plugin
sudo mkdir -p /usr/local/lib/docker/cli-plugins
sudo curl -SL https://github.com/docker/compose/releases/latest/download/docker-compose-linux-x86_64 \
  -o /usr/local/lib/docker/cli-plugins/docker-compose
sudo chmod +x /usr/local/lib/docker/cli-plugins/docker-compose

exit   # log out and back in so the docker group applies
```

> A 1 GB instance can run out of memory during the Maven build. Add swap first:
> ```bash
> sudo dd if=/dev/zero of=/swapfile bs=128M count=16 && sudo chmod 600 /swapfile
> sudo mkswap /swapfile && sudo swapon /swapfile
> echo '/swapfile swap swap defaults 0 0' | sudo tee -a /etc/fstab
> ```

## 3. Deploy

```bash
ssh -i orderhub-key.pem ec2-user@<PUBLIC_IP>
git clone https://github.com/<your-username>/orderhub.git
cd orderhub
cp .env.example .env
nano .env        # set strong DB_PASSWORD, JWT_SECRET (32+ chars) and ADMIN_PASSWORD

docker compose up -d --build
docker compose logs -f app     # wait for "Started OrderHubApplication"
```

Generate a strong secret with `openssl rand -base64 48`.

## 4. Verify

- Health: `http://<PUBLIC_IP>:8080/actuator/health` returns `{"status":"UP"}`
- Docs: `http://<PUBLIC_IP>:8080/swagger-ui.html`

## 5. Update after new commits

```bash
cd orderhub && git pull && docker compose up -d --build
```

## Cost and cleanup

A Free Tier account covers 750 hours a month of `t2.micro`/`t3.micro` for the first 12 months. When you're done
demoing, **stop** the instance (keeps data) or **terminate** it (deletes everything) to avoid charges.
Check your account's Free Tier terms, since they vary by account age.

## Optional upgrades to mention in interviews

- Move PostgreSQL to **Amazon RDS** and set `DB_URL` to the RDS endpoint
- Put **Nginx + Let's Encrypt** in front for HTTPS on port 443
- Push the image to **Amazon ECR** from GitHub Actions and pull it on EC2 instead of building there
