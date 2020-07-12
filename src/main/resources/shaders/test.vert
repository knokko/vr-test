#version 130

in vec3 position;
in vec3 color;

out vec3 passColor;

uniform mat4 matrix;

void main() {
    passColor = color;
    gl_Position = matrix * vec4(position, 1.0);
}