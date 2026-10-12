# Background removal service. The published danielgatis/rembg image has no arm64 build,
# so we install the same open-source tool ourselves (runs natively on Apple Silicon and x86).
FROM python:3.12-slim
RUN pip install --no-cache-dir "rembg[cpu,cli]" pillow-heif
# Teach Pillow (and therefore rembg) to read HEIC/HEIF, the default iPhone photo format.
RUN echo "from pillow_heif import register_heif_opener; register_heif_opener()" \
    > "$(python -c 'import site; print(site.getsitepackages()[0])')/sitecustomize.py"
# Bake the models into the image: no download on the first upload. The backend asks two models for a
# mask and combines them (see CutoutService): u2net and isnet-general-use, each ~170 MB, Apache 2.0.
# rembg's newer default (bria-rmbg, 1 GB) needs more memory and is non-commercial.
RUN rembg d u2net isnet-general-use
EXPOSE 7000
CMD ["rembg", "s", "--host", "0.0.0.0", "--port", "7000"]
